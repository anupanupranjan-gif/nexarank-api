// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.nexarank.api.model.AuditEvent;
import com.nexarank.api.repository.AuditEventRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * NR-155: SHA-256 row chaining for audit_events (ADR-06). Every write goes
 * through {@link #appendChained}, which links the new row to whatever the
 * tenant's chain currently ends with, so a direct DB mutation of any row
 * breaks the chain from that point forward — detectable via
 * {@link #verifyChain}, not just assumed from the absence of an update API.
 */
@Service
public class AuditChainService {

    private static final String GENESIS_HASH = "GENESIS";

    private final AuditEventRepository auditEventRepository;
    private final EntityManager entityManager;

    public AuditChainService(AuditEventRepository auditEventRepository, EntityManager entityManager) {
        this.auditEventRepository = auditEventRepository;
        this.entityManager = entityManager;
    }

    /**
     * Reads the tenant's current chain tail and appends event on top of it,
     * inside one transaction holding a Postgres advisory lock keyed on the
     * tenant. Without the lock, two concurrent writes for the same tenant
     * could both read the same "latest" hash and each append against it,
     * forking the chain — pg_advisory_xact_lock serializes exactly the
     * writers that would otherwise race, without needing an existing row to
     * lock (unlike SELECT ... FOR UPDATE, which has nothing to lock on a
     * tenant's very first audit event).
     */
    @Transactional
    public AuditEvent appendChained(AuditEvent event) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:tenantId))")
                .setParameter("tenantId", event.getTenantId())
                .getSingleResult();

        String prevHash = auditEventRepository.findTopByTenantIdOrderBySeqDesc(event.getTenantId())
                .map(AuditEvent::getRowHash)
                .orElse(GENESIS_HASH);

        event.setPrevHash(prevHash);
        event.setRowHash(computeRowHash(prevHash, event));
        return auditEventRepository.save(event);
    }

    /**
     * Walks a tenant's full chain oldest-first, recomputing each row's hash
     * from its stored content plus the previous row's hash, and comparing it
     * to what's actually stored. The first mismatch is where the chain broke
     * — either that row's content was altered after being written, or a row
     * between it and its recorded predecessor was deleted/inserted outside
     * the normal append path.
     *
     * Rows written before this migration carry the PRE_CHAIN_GENESIS
     * sentinel instead of a real hash (see V50) and are skipped rather than
     * flagged — there's no cryptographic basis to verify data that predates
     * chaining. Verification is meaningful from the first genuinely chained
     * row onward.
     */
    public ChainVerificationResult verifyChain(String tenantId) {
        List<AuditEvent> events = auditEventRepository.findByTenantIdOrderBySeqAsc(tenantId);
        long checked = 0;
        for (AuditEvent event : events) {
            if ("PRE_CHAIN_GENESIS".equals(event.getRowHash())) continue;

            String expectedHash = computeRowHash(event.getPrevHash(), event);
            if (!expectedHash.equals(event.getRowHash())) {
                return ChainVerificationResult.broken(event.getId(), event.getSeq(), checked);
            }
            checked++;
        }
        return ChainVerificationResult.valid(checked);
    }

    /**
     * Delimited concatenation, not JSON — this only needs to change whenever
     * any field's content changes, not to be parsed back. Field order is
     * fixed so the same event always hashes the same way.
     *
     * createdAt is truncated to milliseconds before hashing: Instant.now()
     * carries nanosecond precision, but the DB column is TIMESTAMP, which
     * round-trips at a coarser resolution. Hashing the untruncated in-memory
     * value at write time would never match the value recomputed from what
     * was actually persisted, breaking every row on the very first verify —
     * not real tampering, just two different precisions of the same instant.
     */
    private String computeRowHash(String prevHash, AuditEvent e) {
        String canonical = String.join("|",
                nullSafe(prevHash),
                nullSafe(e.getId()),
                nullSafe(e.getTenantId()),
                nullSafe(e.getProjectId()),
                nullSafe(e.getUserId()),
                nullSafe(e.getUsername()),
                nullSafe(e.getAction()),
                nullSafe(e.getEntity()),
                nullSafe(e.getEntityId()),
                nullSafe(e.getDetails()),
                nullSafe(e.getIpAddress()),
                e.getCreatedAt() != null ? e.getCreatedAt().truncatedTo(ChronoUnit.MILLIS).toString() : "",
                String.valueOf(e.getTier()),
                nullSafe(e.getEntityName()),
                nullSafe(e.getPreviousState()),
                nullSafe(e.getNewState()),
                nullSafe(e.getFieldDiff()),
                nullSafe(e.getReason()));
        return sha256Hex(canonical);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record ChainVerificationResult(boolean valid, long rowsChecked,
                                           String brokenAtEventId, Long brokenAtSeq) {
        static ChainVerificationResult valid(long rowsChecked) {
            return new ChainVerificationResult(true, rowsChecked, null, null);
        }

        static ChainVerificationResult broken(String eventId, Long seq, long rowsCheckedBeforeBreak) {
            return new ChainVerificationResult(false, rowsCheckedBeforeBreak, eventId, seq);
        }
    }
}
