// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarank.api.model.MerchRule;
import com.nexarank.api.model.RuleVersion;
import com.nexarank.api.repository.RuleVersionRepository;
import com.nexarank.api.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Phase 22 / NR-33: rule versioning + rollback.
 * Every mutation of a MerchRule appends a full-state snapshot here.
 * History is append-only — rollback creates a NEW version, nothing is deleted.
 */
@Service
public class RuleVersionService {

    private static final Logger log = LoggerFactory.getLogger(RuleVersionService.class);

    private final RuleVersionRepository repository;
    private final ObjectMapper objectMapper;

    public RuleVersionService(RuleVersionRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Snapshot the full rule state. Called by MerchRuleService after every save.
     * Version numbers are sequential per rule, starting at 1.
     */
    @Transactional
    public RuleVersion snapshot(MerchRule rule, String changedBy, String changeNote) {
        int nextVersion = repository.findTopByRuleIdOrderByVersionNumberDesc(rule.getId())
                .map(v -> v.getVersionNumber() + 1)
                .orElse(1);

        RuleVersion version = new RuleVersion();
        version.setId(UUID.randomUUID().toString());
        version.setRuleId(rule.getId());
        version.setTenantId(rule.getTenantId());
        version.setProjectId(rule.getProjectId());
        version.setVersionNumber(nextVersion);
        version.setSnapshot(serialize(rule));
        version.setChangedBy(changedBy);
        version.setChangedAt(Instant.now());
        version.setChangeNote(changeNote);

        RuleVersion saved = repository.save(version);
        log.info("RULE_VERSION_CREATED ruleId={} version={} by={} note={}",
                rule.getId(), nextVersion, changedBy, changeNote);
        return saved;
    }

    /** List version metadata for a rule (no snapshot bodies — keep the payload small). */
    public List<Map<String, Object>> getHistory(String ruleId) {
        return repository.findByRuleIdOrderByVersionNumberDesc(ruleId).stream()
                .filter(this::belongsToCurrentTenant)
                .map(v -> Map.<String, Object>of(
                        "versionNumber", v.getVersionNumber(),
                        "changedBy", v.getChangedBy() == null ? "system" : v.getChangedBy(),
                        "changedAt", v.getChangedAt(),
                        "changeNote", v.getChangeNote() == null ? "" : v.getChangeNote()))
                .collect(Collectors.toList());
    }

    /** Full rule state at a specific version, parsed back from the snapshot JSON. */
    public Optional<MerchRule> getVersionState(String ruleId, int versionNumber) {
        return repository.findByRuleIdAndVersionNumber(ruleId, versionNumber)
                .filter(this::belongsToCurrentTenant)
                .map(v -> deserialize(v.getSnapshot()));
    }

    /**
     * Restore the rule's mutable fields from an old snapshot.
     * Identity, audit fields, and createdAt are preserved from the current rule.
     * The caller (MerchRuleService) saves the result and snapshots it as a new version.
     */
    public Optional<MerchRule> applyRollback(MerchRule current, int versionNumber) {
        return repository.findByRuleIdAndVersionNumber(current.getId(), versionNumber)
                .filter(this::belongsToCurrentTenant)
                .map(v -> {
                    MerchRule old = deserialize(v.getSnapshot());
                    current.setType(old.getType());
                    current.setQuery(old.getQuery());
                    current.setBoostField(old.getBoostField());
                    current.setBoostValue(old.getBoostValue());
                    current.setBoostFactor(old.getBoostFactor());
                    current.setPinnedIdsJson(old.getPinnedIdsJson());
                    current.setSynonymsJson(old.getSynonymsJson());
                    current.setPriority(old.getPriority());
                    current.setActivateAt(old.getActivateAt());
                    current.setExpireAt(old.getExpireAt());
                    return current;
                });
    }

    private boolean belongsToCurrentTenant(RuleVersion v) {
        return v.getTenantId().equals(TenantContext.getTenantId())
                && v.getProjectId().equals(TenantContext.getProjectId());
    }

    private String serialize(MerchRule rule) {
        try {
            return objectMapper.writeValueAsString(rule);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize rule snapshot for " + rule.getId(), e);
        }
    }

    private MerchRule deserialize(String json) {
        try {
            return objectMapper.readValue(json, MerchRule.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse rule snapshot", e);
        }
    }
}
