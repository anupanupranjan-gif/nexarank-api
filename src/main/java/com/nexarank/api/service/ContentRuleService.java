// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarank.api.model.ContentRule;
import com.nexarank.api.model.ContentZone;
import com.nexarank.api.model.RuleTriggerCondition;
import com.nexarank.api.repository.ContentRuleRepository;
import com.nexarank.api.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * NR-84: Content Rule CRUD + maker-checker approval workflow.
 * NR-83: evaluation logic for the content enrich endpoint.
 * Mirrors MerchRuleService's conventions (entity-replacement update,
 * DRAFT/PENDING_REVIEW/status transitions, per-mutation versioning).
 */
@Service
public class ContentRuleService {

    private static final Logger log = LoggerFactory.getLogger(ContentRuleService.class);

    private final ContentRuleRepository repository;
    private final ContentRuleVersionService versionService;
    private final ObjectMapper objectMapper;

    public ContentRuleService(ContentRuleRepository repository,
                               ContentRuleVersionService versionService,
                               ObjectMapper objectMapper) {
        this.repository = repository;
        this.versionService = versionService;
        this.objectMapper = objectMapper;
    }

    // ── CRUD (NR-84) ─────────────────────────────────────────────────────────

    public ContentRule createRule(ContentRule rule) {
        if (rule.getId() == null) rule.setId(UUID.randomUUID().toString());
        if (rule.getTenantId() == null) rule.setTenantId(TenantContext.getTenantId());
        if (rule.getProjectId() == null) rule.setProjectId(TenantContext.getProjectId());
        String currentUser = getCurrentUsername();
        rule.setCreatedBy(currentUser);
        rule.setStatus(ContentRule.ContentRuleStatus.DRAFT);
        rule.setCreatedAt(Instant.now());
        rule.setUpdatedAt(Instant.now());
        serializeTransientFields(rule);
        ContentRule saved = repository.save(rule);
        versionService.snapshot(saved, currentUser, "Content rule created");
        log.info("CONTENT_RULE_CREATED zone={} name={} by={}", rule.getZone(), rule.getName(), currentUser);
        return saved;
    }

    public Optional<ContentRule> getById(String id) {
        return findScopedById(id)
                .filter(r -> r.getDeletedAt() == null)
                .map(this::withDeserializedFields);
    }

    /**
     * NR-162/NR-152 fix: every findById(id) below used to have no tenant
     * filter at all, and ContentRule had no projectId to filter on in the
     * first place — it was never brought under NR-121's project-scoping
     * model. Both gaps are closed together: ContentRule now carries
     * project_id (V52 migration) and every by-ID lookup checks both.
     */
    private Optional<ContentRule> findScopedById(String id) {
        return repository.findById(id)
                .filter(r -> r.getTenantId().equals(TenantContext.getTenantId())
                        && r.getProjectId().equals(TenantContext.getProjectId()));
    }

    public Page<ContentRule> list(ContentZone zone, ContentRule.ContentRuleStatus status, int page, int size) {
        List<ContentRule> all = repository.findByTenantIdAndProjectIdAndDeletedAtIsNull(
                TenantContext.getTenantId(), TenantContext.getProjectId());
        List<ContentRule> filtered = all.stream()
                .filter(r -> zone == null || r.getZone() == zone)
                .filter(r -> status == null || r.getStatus() == status)
                .map(this::withDeserializedFields)
                .sorted(Comparator.comparingInt(ContentRule::getPriority).reversed())
                .toList();

        int from = Math.min(page * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        return new PageImpl<>(filtered.subList(from, to), PageRequest.of(page, size), filtered.size());
    }

    /** NR-164: unpaged, undeleted list for config export — list() above is paginated for the UI. */
    public List<ContentRule> getAllForExport() {
        return repository.findByTenantIdAndProjectIdAndDeletedAtIsNull(
                        TenantContext.getTenantId(), TenantContext.getProjectId())
                .stream()
                .map(this::withDeserializedFields)
                .toList();
    }

    public Optional<ContentRule> updateRule(String id, ContentRule updated) {
        return findScopedById(id).filter(r -> r.getDeletedAt() == null).map(existing -> {
            updated.setId(existing.getId());
            updated.setTenantId(existing.getTenantId());
            updated.setProjectId(existing.getProjectId());
            updated.setCreatedBy(existing.getCreatedBy());
            updated.setCreatedAt(existing.getCreatedAt());
            updated.setSubmittedBy(existing.getSubmittedBy());
            updated.setApprovedBy(existing.getApprovedBy());
            updated.setDeletedAt(null);
            // DRAFT rules haven't been submitted yet, so editing keeps them DRAFT.
            // Anything already submitted re-enters review on edit (same as MerchRule).
            updated.setStatus(existing.getStatus() == ContentRule.ContentRuleStatus.DRAFT
                    ? ContentRule.ContentRuleStatus.DRAFT
                    : ContentRule.ContentRuleStatus.PENDING_REVIEW);
            updated.setUpdatedAt(Instant.now());
            serializeTransientFields(updated);
            ContentRule saved = repository.save(updated);
            versionService.snapshot(saved, getCurrentUsername(), "Content rule updated");
            return saved;
        });
    }

    public void deleteRule(String id) {
        findScopedById(id).ifPresent(rule -> {
            rule.setDeletedAt(Instant.now());
            rule.setUpdatedAt(Instant.now());
            repository.save(rule);
            versionService.snapshot(rule, getCurrentUsername(), "Content rule deleted");
            log.info("CONTENT_RULE_DELETED id={} by={}", id, getCurrentUsername());
        });
    }

    // ── Maker-checker workflow (NR-84) ──────────────────────────────────────

    public Optional<ContentRule> submitForReview(String id) {
        String currentUser = getCurrentUsername();
        return findScopedById(id).filter(r -> r.getDeletedAt() == null).map(rule -> {
            if (rule.getStatus() != ContentRule.ContentRuleStatus.DRAFT) {
                throw new IllegalArgumentException(
                        "Only DRAFT content rules can be submitted for review (current status: " + rule.getStatus() + ")");
            }
            rule.setStatus(ContentRule.ContentRuleStatus.PENDING_REVIEW);
            rule.setSubmittedBy(currentUser);
            rule.setUpdatedAt(Instant.now());
            ContentRule saved = repository.save(rule);
            versionService.snapshot(saved, currentUser, "Submitted for review");
            log.info("CONTENT_RULE_SUBMITTED id={} by={}", rule.getId(), currentUser);
            return saved;
        });
    }

    public Optional<ContentRule> approveRule(String id, String comment) {
        String currentUser = getCurrentUsername();
        return findScopedById(id).filter(r -> r.getDeletedAt() == null).map(rule -> {
            if (rule.getStatus() != ContentRule.ContentRuleStatus.PENDING_REVIEW) {
                throw new IllegalArgumentException(
                        "Only PENDING_REVIEW content rules can be approved (current status: " + rule.getStatus() + ")");
            }
            rule.setStatus(ContentRule.ContentRuleStatus.ACTIVE);
            rule.setApprovedBy(currentUser);
            rule.setRejectionComment(null);
            rule.setUpdatedAt(Instant.now());
            ContentRule saved = repository.save(rule);
            versionService.snapshot(saved, currentUser,
                    comment == null || comment.isBlank() ? "Content rule approved" : "Content rule approved: " + comment);
            log.info("CONTENT_RULE_APPROVED id={} by={}", rule.getId(), currentUser);
            return saved;
        });
    }

    /**
     * Rejected rules bounce back to DRAFT with the reason attached, same
     * convention as MerchRuleService.rejectRule.
     */
    public Optional<ContentRule> rejectRule(String id, String comment) {
        String currentUser = getCurrentUsername();
        return findScopedById(id).filter(r -> r.getDeletedAt() == null).map(rule -> {
            rule.setStatus(ContentRule.ContentRuleStatus.DRAFT);
            rule.setApprovedBy(currentUser);
            rule.setRejectionComment(comment);
            rule.setUpdatedAt(Instant.now());
            ContentRule saved = repository.save(rule);
            versionService.snapshot(saved, currentUser,
                    comment == null || comment.isBlank() ? "Content rule rejected" : "Content rule rejected: " + comment);
            log.info("CONTENT_RULE_REJECTED id={} by={}", rule.getId(), currentUser);
            return saved;
        });
    }

    public List<Map<String, Object>> getHistory(String id) {
        // NR-162/NR-152: scope-check the rule itself before returning any of
        // its version history — findScopedById 404s for a rule outside the
        // caller's tenant+project the same way getById does.
        return findScopedById(id)
                .map(r -> versionService.getHistory(id))
                .orElse(List.of());
    }

    // ── Enrich evaluation (NR-83) ───────────────────────────────────────────

    /**
     * For each requested zone, find ACTIVE content rules that match the
     * given context and are within their schedule window, then return the
     * single highest-priority match (higher priority number wins).
     */
    public Map<ContentZone, ContentRule> resolveZones(List<ContentZone> zones, Map<String, String> context) {
        Instant now = Instant.now();
        String tenantId = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();
        List<ContentRule> candidates = repository.findByTenantIdAndProjectIdAndZoneInAndStatusAndDeletedAtIsNull(
                        tenantId, projectId, zones, ContentRule.ContentRuleStatus.ACTIVE)
                .stream()
                .map(this::withDeserializedFields)
                .filter(r -> r.getScheduleStart() == null || !r.getScheduleStart().isAfter(now))
                .filter(r -> r.getScheduleEnd() == null || r.getScheduleEnd().isAfter(now))
                .filter(r -> conditionsMatch(r.getTriggerConditions(), context))
                .toList();

        Map<ContentZone, ContentRule> winners = new java.util.LinkedHashMap<>();
        for (ContentZone zone : zones) {
            candidates.stream()
                    .filter(r -> r.getZone() == zone)
                    .max(Comparator.comparingInt(ContentRule::getPriority))
                    .ifPresent(winner -> winners.put(zone, winner));
        }
        return winners;
    }

    /**
     * Same AND-across-conditions/OR-within-values semantics as
     * RuleTriggerConditionService, applied to the in-memory (JSON-backed)
     * condition list instead of the separate rule_trigger_conditions table.
     */
    private boolean conditionsMatch(List<RuleTriggerCondition> conditions, Map<String, String> context) {
        if (conditions == null || conditions.isEmpty()) return true;
        if (context == null || context.isEmpty()) return false;
        for (RuleTriggerCondition condition : conditions) {
            String actual = context.get(condition.getFacetField());
            if (actual == null) return false;
            List<String> allowed = condition.getFacetValues();
            if (allowed == null || allowed.isEmpty()) return false;
            boolean matches = allowed.stream().anyMatch(v -> v.equalsIgnoreCase(actual));
            if (!matches) return false;
        }
        return true;
    }

    // ── JSON <-> transient field helpers ─────────────────────────────────────

    private ContentRule withDeserializedFields(ContentRule rule) {
        try {
            if (rule.getTriggerConditionsJson() != null && !rule.getTriggerConditionsJson().isBlank()) {
                rule.setTriggerConditions(objectMapper.readValue(rule.getTriggerConditionsJson(),
                        new TypeReference<List<RuleTriggerCondition>>() {}));
            }
            if (rule.getContentPayloadJson() != null && !rule.getContentPayloadJson().isBlank()) {
                rule.setContentPayload(objectMapper.readValue(rule.getContentPayloadJson(),
                        new TypeReference<Map<String, String>>() {}));
            }
        } catch (Exception e) {
            log.warn("Failed to deserialize transient fields for content rule {}: {}", rule.getId(), e.getMessage());
        }
        return rule;
    }

    private void serializeTransientFields(ContentRule rule) {
        try {
            if (rule.getTriggerConditions() != null) {
                rule.setTriggerConditionsJson(objectMapper.writeValueAsString(rule.getTriggerConditions()));
            }
            if (rule.getContentPayload() != null) {
                rule.setContentPayloadJson(objectMapper.writeValueAsString(rule.getContentPayload()));
            }
        } catch (Exception e) {
            log.warn("Failed to serialize transient fields for content rule {}: {}", rule.getId(), e.getMessage());
        }
    }

    private String getCurrentUsername() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
