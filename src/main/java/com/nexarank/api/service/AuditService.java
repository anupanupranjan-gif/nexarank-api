// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.nexarank.api.model.AuditEvent;
import com.nexarank.api.model.MerchRule;
import com.nexarank.api.repository.AuditEventRepository;
import com.nexarank.api.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final AuditEventRepository auditEventRepository;
    private final AuditDiffService diffService;

    public AuditService(AuditEventRepository auditEventRepository, AuditDiffService diffService) {
        this.auditEventRepository = auditEventRepository;
        this.diffService = diffService;
    }

    public void log(String action, String entity, String entityId, String details) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "system";
        save(action, entity, entityId, details, username);
    }

    /**
     * NR-68: for transitions the system performs on its own (e.g. tenant
     * auto-publish cascading APPROVED -> LIVE), regardless of which human
     * triggered the surrounding request.
     */
    public void logAsSystem(String action, String entity, String entityId, String details) {
        save(action, entity, entityId, details, "system");
    }

    /**
     * NR-70 Tier 1: a rule state change, with the rule's display name
     * denormalized at write time and a structural field-level diff attached.
     *
     * Never throws — an audit write must not be able to fail the business
     * operation it records (same contract the Tier 2 path has always had).
     *
     * @param before rule state prior to the change, or null for create/delete
     * @param after  rule state after the change, or null for delete
     */
    public void logRuleChange(String action, MerchRule before, MerchRule after,
                              String reason, String actor) {
        try {
            MerchRule subject = after != null ? after : before;
            if (subject == null) return;

            AuditEvent event = new AuditEvent();
            event.setId(UUID.randomUUID().toString());
            event.setTier(1);
            event.setTenantId(subject.getTenantId() != null
                    ? subject.getTenantId() : TenantContext.getTenantId());
            event.setProjectId(subject.getProjectId() != null
                    ? subject.getProjectId() : TenantContext.getProjectId());
            event.setUsername(actor);
            event.setAction(action);
            event.setEntity("MerchRule");
            event.setEntityId(subject.getId());
            event.setEntityName(displayName(subject));
            event.setPreviousState(before != null && before.getStatus() != null
                    ? before.getStatus().name() : null);
            event.setNewState(after != null && after.getStatus() != null
                    ? after.getStatus().name() : null);
            event.setReason(reason);

            var changes = diffService.diff(before, after);
            event.setFieldDiff(diffService.toJson(changes));
            event.setDetails(diffService.toSummary(changes));
            event.setCreatedAt(Instant.now());

            auditEventRepository.save(event);

            log.info("AUDIT action={} entity=MerchRule id={} name={} actor={} tenant={} project={} tier=1",
                    action, subject.getId(), event.getEntityName(), actor,
                    event.getTenantId(), event.getProjectId());
        } catch (Exception e) {
            log.warn("Failed to save tier-1 audit event: {}", e.getMessage());
        }
    }

    /**
     * MerchRule has no dedicated name field, so the display label follows the
     * same "[TYPE] query" convention already used in the rules table and in
     * NR-119's A/B test variant summaries.
     */
    private String displayName(MerchRule rule) {
        String type = rule.getType() != null ? rule.getType().name() : "RULE";
        String query = rule.getQuery() != null && !rule.getQuery().isBlank()
                ? rule.getQuery() : "(all queries)";
        return type + " " + query;
    }

    private void save(String action, String entity, String entityId, String details, String username) {
        try {
            AuditEvent event = new AuditEvent();
            event.setId(UUID.randomUUID().toString());
            event.setTier(2);
            event.setTenantId(TenantContext.getTenantId());
            event.setProjectId(TenantContext.getProjectId());
            event.setUsername(username);
            event.setAction(action);
            event.setEntity(entity);
            event.setEntityId(entityId);
            event.setDetails(details);
            event.setCreatedAt(Instant.now());

            auditEventRepository.save(event);

            // Structured, single-line audit marker for log-based observability
            // (Loki "NexaRank Audit Log" dashboard). Every audit action lands
            // here, so all lifecycle events are visible in logs uniformly — not
            // just the few that services also log ad hoc.
            log.info("AUDIT action={} entity={} id={} actor={} tenant={} project={}",
                    action, entity, entityId, username,
                    event.getTenantId(), event.getProjectId());
        } catch (Exception e) {
            log.warn("Failed to save audit event: {}", e.getMessage());
        }
    }

    public Page<AuditEvent> getAuditLog(int page, int size) {
        return auditEventRepository.findByTenantIdOrderByCreatedAtDesc(
                TenantContext.getTenantId(),
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    public Page<AuditEvent> getAuditLogByProject(int page, int size) {
        return auditEventRepository.findByTenantIdAndProjectIdOrderByCreatedAtDesc(
                TenantContext.getTenantId(),
                TenantContext.getProjectId(),
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }
}
