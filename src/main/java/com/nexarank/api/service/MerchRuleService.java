// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;
import com.nexarank.api.security.TenantContext;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.util.UUID;

import com.nexarank.api.model.MerchRule;
import com.nexarank.api.model.Tenant;
import com.nexarank.api.repository.MerchRuleRepository;
import com.nexarank.api.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class MerchRuleService {

    private static final Logger log = LoggerFactory.getLogger(MerchRuleService.class);

    private final MerchRuleRepository repository;
    private final RuleVersionService versionService;
    private final RuleTriggerConditionService triggerService;
    private final RuleNotificationService notificationService;
    private final TenantRepository tenantRepository;
    private final AuditService auditService;
    private final RulesCacheVersionService cacheVersionService;

    public MerchRuleService(MerchRuleRepository repository,
                             RuleVersionService versionService,
                             RuleTriggerConditionService triggerService,
                             RuleNotificationService notificationService,
                             TenantRepository tenantRepository,
                             AuditService auditService,
                             RulesCacheVersionService cacheVersionService) {
        this.repository     = repository;
        this.versionService = versionService;
        this.triggerService = triggerService;
        this.notificationService = notificationService;
        this.tenantRepository = tenantRepository;
        this.auditService = auditService;
        this.cacheVersionService = cacheVersionService;
    }

    public List<MerchRule> getAllRules() {
        List<MerchRule> rules = repository.findByTenantIdAndProjectId(
                TenantContext.getTenantId(), TenantContext.getProjectId());
        rules.forEach(r -> {
            r.setTriggerConditions(triggerService.getConditions(r.getId()));
            deserializeTransientFields(r);
        });
        return rules;
    }

    public List<MerchRule> getPendingRules() {
        return getAllRules().stream()
                .filter(r -> r.getStatus() == MerchRule.RuleStatus.PENDING_REVIEW)
                .toList();
    }

    /**
     * NR-68: rules that are actually serving live search traffic.
     * APPROVED alone is not enough — an ADMIN must separately promote to LIVE.
     */
    public List<MerchRule> getLiveRules() {
        Instant now = Instant.now();
        return getAllRules().stream()
                .filter(r -> r.getStatus() == MerchRule.RuleStatus.LIVE && r.isEnabled())
                .filter(r -> r.getActivateAt() == null || r.getActivateAt().isBefore(now))
                .filter(r -> r.getExpireAt() == null || r.getExpireAt().isAfter(now))
                .toList();
    }

    public List<MerchRule> getRulesByQuery(String query) {
        return getLiveRules().stream()
                .filter(r -> query.equalsIgnoreCase(r.getQuery()))
                .toList();
    }

    public MerchRule createRule(MerchRule rule) {
        validateRedirectUrl(rule);
        if (rule.getId() == null) rule.setId(UUID.randomUUID().toString());
        if (rule.getTenantId() == null) rule.setTenantId(TenantContext.getTenantId());
        if (rule.getProjectId() == null) rule.setProjectId(TenantContext.getProjectId());
        String currentUser = getCurrentUsername();
        rule.setSubmittedBy(currentUser);
        rule.setStatus(MerchRule.RuleStatus.DRAFT);
        rule.setEnabled(false);
        rule.setCreatedAt(Instant.now());
        rule.setUpdatedAt(Instant.now());
        serializeTransientFields(rule);
        MerchRule saved = repository.save(rule);
        versionService.snapshot(saved, currentUser, "Rule created");
        if (rule.getTriggerConditions() != null && !rule.getTriggerConditions().isEmpty()) {
            java.util.List<java.util.Map<String, Object>> dtos = rule.getTriggerConditions().stream()
                    .map(c -> { java.util.Map<String, Object> d = new java.util.HashMap<>();
                        d.put("facetField", c.getFacetField());
                        d.put("facetValues", c.getFacetValues());
                        return d; }).toList();
            triggerService.saveConditions(saved.getId(), dtos);
        }
        log.info("RULE_CREATED type={} query={} by={}", rule.getType(), rule.getQuery(), rule.getSubmittedBy());
        cacheVersionService.bump(saved.getTenantId(), saved.getProjectId());
        return saved;
    }

    public Optional<MerchRule> getById(String id) {
        return repository.findById(id).map(rule -> {
            rule.setTriggerConditions(triggerService.getConditions(id));
            deserializeTransientFields(rule);
            return rule;
        });
    }

    /**
     * NR-119: batch fetch for enrichment (e.g. A/B test rule summaries) — a
     * plain findAllById, so DISABLED rules are still returned (they're never
     * deleted, only disabled — see RuleAbTestService.promoteWinner). Trigger
     * conditions aren't loaded since callers only need type/query/action for
     * a one-line summary, not the full edit-form shape.
     */
    public Map<String, MerchRule> getByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        List<MerchRule> found = repository.findAllById(ids);
        found.forEach(this::deserializeTransientFields);
        return found.stream().collect(Collectors.toMap(MerchRule::getId, r -> r));
    }

    public Optional<MerchRule> updateRule(String id, MerchRule updated) {
        validateRedirectUrl(updated);
        return repository.findById(id).map(existing -> {
            updated.setId(existing.getId());
            updated.setTenantId(existing.getTenantId());
            updated.setProjectId(existing.getProjectId());
            updated.setSubmittedBy(existing.getSubmittedBy());
            // DRAFT rules haven't been submitted yet, so editing keeps them DRAFT.
            // Anything already submitted (or beyond) re-enters review on edit.
            updated.setStatus(existing.getStatus() == MerchRule.RuleStatus.DRAFT
                    ? MerchRule.RuleStatus.DRAFT
                    : MerchRule.RuleStatus.PENDING_REVIEW);
            updated.setEnabled(false);
            updated.setCreatedAt(existing.getCreatedAt());
            updated.setUpdatedAt(Instant.now());
            // Preserve the zero-result-query link (NR-69) across edits unless the
            // caller explicitly supplies a new one — otherwise a routine edit (e.g.
            // tweaking the AI-suggested synonyms) would silently null it out, since
            // this endpoint replaces the whole entity from the request body.
            if (updated.getSourceZeroResultQuery() == null) {
                updated.setSourceZeroResultQuery(existing.getSourceZeroResultQuery());
            }
            serializeTransientFields(updated);
            MerchRule saved = repository.save(updated);
            versionService.snapshot(saved, getCurrentUsername(), "Rule updated");
            if (updated.getTriggerConditions() != null) {
                java.util.List<java.util.Map<String, Object>> dtos = updated.getTriggerConditions().stream()
                        .map(c -> { java.util.Map<String, Object> d = new java.util.HashMap<>();
                            d.put("facetField", c.getFacetField());
                            d.put("facetValues", c.getFacetValues());
                            return d; }).toList();
                triggerService.saveConditions(saved.getId(), dtos);
            }
            cacheVersionService.bump(saved.getTenantId(), saved.getProjectId());
            return saved;
        });
    }

    /**
     * NR-68: MERCHANDISER submits a DRAFT rule for APPROVER review.
     */
    public Optional<MerchRule> submitForReview(String id) {
        String currentUser = getCurrentUsername();
        return repository.findById(id).map(rule -> {
            if (rule.getStatus() != MerchRule.RuleStatus.DRAFT) {
                throw new IllegalArgumentException(
                        "Only DRAFT rules can be submitted for review (current status: " + rule.getStatus() + ")");
            }
            rule.setStatus(MerchRule.RuleStatus.PENDING_REVIEW);
            rule.setUpdatedAt(Instant.now());
            MerchRule saved = repository.save(rule);
            versionService.snapshot(saved, currentUser, "Submitted for review");
            log.info("RULE_SUBMITTED id={} query={} by={}", rule.getId(), rule.getQuery(), currentUser);
            cacheVersionService.bump(saved.getTenantId(), saved.getProjectId());
            notificationService.notifySubmitted(saved);
            return saved;
        });
    }

    /**
     * NR-68: PENDING_REVIEW -> APPROVED, then possibly straight on to LIVE in
     * this same action — either because the tenant has auto-publish on
     * (default), or because the approver explicitly chose "approve and
     * publish" (publishNow). Otherwise the rule sits at APPROVED until an
     * ADMIN/TENANT_ADMIN calls promoteToLive separately.
     */
    public Optional<MerchRule> approveRule(String id, String comment, boolean publishNow) {
        String currentUser = getCurrentUsername();
        return repository.findById(id).map(rule -> {
            rule.setStatus(MerchRule.RuleStatus.APPROVED);
            rule.setApprovedBy(currentUser);
            rule.setReviewComment(comment);
            rule.setUpdatedAt(Instant.now());
            MerchRule saved = repository.save(rule);
            versionService.snapshot(saved, currentUser,
                    comment == null || comment.isBlank() ? "Rule approved" : "Rule approved: " + comment);
            log.info("RULE_APPROVED id={} query={} by={} comment={}", rule.getId(), rule.getQuery(), rule.getApprovedBy(), comment);

            boolean autoPublish = tenantRepository.findById(saved.getTenantId())
                    .map(Tenant::isAutoPublishRules).orElse(true);
            boolean wentLive = false;
            if (autoPublish) {
                saved = doPromoteToLive(saved, "system", "Auto-published (tenant auto-publish enabled)");
                auditService.logAsSystem("RULE_PROMOTED_LIVE", "MerchRule", saved.getId(), "auto-publish");
                wentLive = true;
            } else if (publishNow) {
                saved = doPromoteToLive(saved, currentUser, "Approved and published by " + currentUser);
                auditService.log("RULE_PROMOTED_LIVE", "MerchRule", saved.getId(), "approver-initiated publish");
                wentLive = true;
            }

            notificationService.notifyApproved(saved, comment, wentLive);
            return saved;
        });
    }

    /**
     * NR-68: ADMIN/TENANT_ADMIN manually promotes an APPROVED rule to LIVE —
     * used when the tenant's auto-publish setting is off and the approver
     * didn't publish at approval time. Sends its own "now live" email since
     * the earlier approval email would have said "pending publish."
     */
    public Optional<MerchRule> promoteToLive(String id) {
        String currentUser = getCurrentUsername();
        return repository.findById(id).map(rule -> {
            if (rule.getStatus() != MerchRule.RuleStatus.APPROVED) {
                throw new IllegalArgumentException(
                        "Only APPROVED rules can be promoted to LIVE (current status: " + rule.getStatus() + ")");
            }
            MerchRule saved = doPromoteToLive(rule, currentUser, "Promoted to live");
            log.info("RULE_PROMOTED_LIVE id={} query={} by={}", rule.getId(), rule.getQuery(), currentUser);
            notificationService.notifyPublished(saved);
            return saved;
        });
    }

    /**
     * NR-68: ADMIN/TENANT_ADMIN force-reverts a LIVE rule back to APPROVED
     * (default) or DRAFT, immediately taking it off search traffic.
     */
    public Optional<MerchRule> demoteFromLive(String id, String targetStatus) {
        String currentUser = getCurrentUsername();
        MerchRule.RuleStatus target;
        try {
            target = (targetStatus == null || targetStatus.isBlank())
                    ? MerchRule.RuleStatus.APPROVED
                    : MerchRule.RuleStatus.valueOf(targetStatus.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown target status: " + targetStatus);
        }
        if (target != MerchRule.RuleStatus.APPROVED && target != MerchRule.RuleStatus.DRAFT) {
            throw new IllegalArgumentException("Rules can only be demoted to APPROVED or DRAFT, not " + target);
        }
        return repository.findById(id).map(rule -> {
            if (rule.getStatus() != MerchRule.RuleStatus.LIVE) {
                throw new IllegalArgumentException(
                        "Only LIVE rules can be demoted (current status: " + rule.getStatus() + ")");
            }
            rule.setStatus(target);
            rule.setEnabled(false);
            rule.setUpdatedAt(Instant.now());
            MerchRule saved = repository.save(rule);
            versionService.snapshot(saved, currentUser, "Reverted from live to " + target);
            log.info("RULE_DEMOTED id={} query={} to={} by={}", rule.getId(), rule.getQuery(), target, currentUser);
            cacheVersionService.bump(saved.getTenantId(), saved.getProjectId());
            return saved;
        });
    }

    private MerchRule doPromoteToLive(MerchRule rule, String actor, String versionNote) {
        rule.setStatus(MerchRule.RuleStatus.LIVE);
        rule.setEnabled(true);
        rule.setUpdatedAt(Instant.now());
        MerchRule saved = repository.save(rule);
        versionService.snapshot(saved, actor, versionNote);
        cacheVersionService.bump(saved.getTenantId(), saved.getProjectId());
        return saved;
    }

    /**
     * NR-68: rejected rules bounce back to DRAFT with the reason attached
     * (rather than sitting in a persisted REJECTED state) so the creator can
     * fix and resubmit. REJECTED remains the audit-log action name/version note.
     */
    public Optional<MerchRule> rejectRule(String id, String comment) {
        String currentUser = getCurrentUsername();
        return repository.findById(id).map(rule -> {
            rule.setStatus(MerchRule.RuleStatus.DRAFT);
            rule.setEnabled(false);
            rule.setApprovedBy(currentUser);
            rule.setReviewComment(comment);
            rule.setUpdatedAt(Instant.now());
            MerchRule saved = repository.save(rule);
            versionService.snapshot(saved, currentUser,
                    comment == null || comment.isBlank() ? "Rule rejected" : "Rule rejected: " + comment);
            log.info("RULE_REJECTED id={} query={} by={} comment={}", rule.getId(), rule.getQuery(), rule.getApprovedBy(), comment);
            cacheVersionService.bump(saved.getTenantId(), saved.getProjectId());
            notificationService.notifyRejected(saved, comment);
            return saved;
        });
    }

    public Optional<MerchRule> toggleRule(String id) {
        return repository.findById(id).map(rule -> {
            rule.setEnabled(!rule.isEnabled());
            rule.setUpdatedAt(Instant.now());
            MerchRule saved = repository.save(rule);
            cacheVersionService.bump(saved.getTenantId(), saved.getProjectId());
            return saved;
        });
    }

    public void deleteRule(String id) {
        repository.findById(id).ifPresent(rule ->
                cacheVersionService.bump(rule.getTenantId(), rule.getProjectId()));
        repository.deleteById(id);
    }

    public Optional<MerchRule> rollbackRule(String id, int versionNumber) {
        String currentUser = getCurrentUsername();
        return repository.findById(id)
                .flatMap(current -> versionService.applyRollback(current, versionNumber))
                .map(restored -> {
                    restored.setStatus(MerchRule.RuleStatus.PENDING_REVIEW);
                    restored.setEnabled(false);
                    restored.setUpdatedAt(Instant.now());
                    MerchRule saved = repository.save(restored);
                    versionService.snapshot(saved, currentUser, "Restored from v" + versionNumber);
                    log.info("RULE_ROLLBACK id={} toVersion={} by={}", id, versionNumber, currentUser);
                    cacheVersionService.bump(saved.getTenantId(), saved.getProjectId());
                    return saved;
                });
    }

    public List<Map<String, Object>> detectConflicts(String query) {
        String tenantId = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();
        List<MerchRule> rules = repository.findByTenantIdAndProjectIdAndQueryAndEnabled(
                tenantId, projectId, query, true);

        List<Map<String, Object>> conflicts = new ArrayList<>();

        boolean hasPin = rules.stream().anyMatch(r -> r.getType() == MerchRule.RuleType.PIN &&
                r.getStatus() == MerchRule.RuleStatus.LIVE);
        boolean hasBoost = rules.stream().anyMatch(r -> r.getType() == MerchRule.RuleType.BOOST &&
                r.getStatus() == MerchRule.RuleStatus.LIVE);
        boolean hasBury = rules.stream().anyMatch(r -> r.getType() == MerchRule.RuleType.BURY &&
                r.getStatus() == MerchRule.RuleStatus.LIVE);

        if (hasPin && hasBoost) {
            Map<String, Object> conflict = new java.util.LinkedHashMap<>();
            conflict.put("type", "PIN_BOOST_CONFLICT");
            conflict.put("query", query);
            conflict.put("message", "Both PIN and BOOST rules active for query '" + query + "'. PIN takes precedence.");
            conflict.put("severity", "WARNING");
            conflicts.add(conflict);
        }
        if (hasBoost && hasBury) {
            Map<String, Object> conflict = new java.util.LinkedHashMap<>();
            conflict.put("type", "BOOST_BURY_CONFLICT");
            conflict.put("query", query);
            conflict.put("message", "Both BOOST and BURY rules active for query '" + query + "'. Check that different products are targeted.");
            conflict.put("severity", "WARNING");
            conflicts.add(conflict);
        }
        if (hasPin && hasBury) {
            Map<String, Object> conflict = new java.util.LinkedHashMap<>();
            conflict.put("type", "PIN_BURY_CONFLICT");
            conflict.put("query", query);
            conflict.put("message", "Both PIN and BURY rules active for query '" + query + "'.");
            conflict.put("severity", "INFO");
            conflicts.add(conflict);
        }

        rules.stream()
            .filter(r -> r.getStatus() == MerchRule.RuleStatus.LIVE)
            .collect(java.util.stream.Collectors.groupingBy(MerchRule::getType))
            .forEach((type, typeRules) -> {
                if (typeRules.size() > 1) {
                    Map<String, Object> conflict = new java.util.LinkedHashMap<>();
                    conflict.put("type", "DUPLICATE_RULE_TYPE");
                    conflict.put("query", query);
                    conflict.put("message", typeRules.size() + " " + type + " rules for query '" + query + "'. Highest priority wins.");
                    conflict.put("severity", "INFO");
                    conflicts.add(conflict);
                }
            });

        return conflicts;
    }

    public Map<String, Object> previewRule(MerchRule rule) {
        String tenantId = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();

        List<MerchRule> existing = repository.findByTenantIdAndProjectIdAndQueryAndEnabled(
                tenantId, projectId, rule.getQuery(), true)
                .stream()
                .filter(r -> r.getStatus() == MerchRule.RuleStatus.LIVE)
                .sorted(java.util.Comparator.comparingInt(MerchRule::getPriority))
                .collect(java.util.stream.Collectors.toList());

        List<Map<String, Object>> conflicts = detectConflicts(rule.getQuery());
        List<Map<String, Object>> duplicateTriggers = findDuplicateTriggers(rule);

        Map<String, Object> preview = new java.util.LinkedHashMap<>();
        preview.put("query", rule.getQuery());
        preview.put("newRule", Map.of(
            "type", rule.getType(),
            "priority", rule.getPriority()
        ));
        preview.put("existingActiveRules", existing.stream().map(r -> Map.of(
            "id", r.getId(),
            "type", r.getType(),
            "priority", r.getPriority(),
            "status", r.getStatus()
        )).collect(java.util.stream.Collectors.toList()));
        preview.put("conflicts", conflicts);
        preview.put("duplicateTriggers", duplicateTriggers);
        preview.put("willApply", rule.getStatus() == null || rule.getStatus() != MerchRule.RuleStatus.REJECTED);

        return preview;
    }

    /**
     * NR-106 / ADR-013: soft (non-blocking) duplicate-trigger warning. Finds
     * other rules of the same type, same query text, and an exactly-matching
     * set of trigger conditions as the candidate — the ADR's own example is
     * two merchandisers both writing a rule for the same term. "Other ACTIVE
     * rules" is interpreted as LIVE + enabled, this codebase's actual
     * serving state (MerchRule has no separate ACTIVE status — LIVE is the
     * one that serves traffic, per NR-68), not a hard filter on DRAFT/
     * PENDING_REVIEW rules that aren't live yet. Never blocks save/submit —
     * callers just surface the result as a warning.
     */
    public List<Map<String, Object>> findDuplicateTriggers(MerchRule candidate) {
        if (candidate.getType() == null || candidate.getQuery() == null || candidate.getQuery().isBlank()) {
            return List.of();
        }
        String tenantId = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();
        java.util.Set<String> candidateKeys = conditionKeys(candidate.getTriggerConditions());

        List<MerchRule> others = repository.findByTenantIdAndProjectId(tenantId, projectId).stream()
                .filter(r -> candidate.getId() == null || !r.getId().equals(candidate.getId()))
                .filter(r -> r.getStatus() == MerchRule.RuleStatus.LIVE && r.isEnabled())
                .filter(r -> r.getType() == candidate.getType())
                .filter(r -> candidate.getQuery().equalsIgnoreCase(r.getQuery()))
                .toList();

        List<Map<String, Object>> matches = new ArrayList<>();
        for (MerchRule other : others) {
            java.util.Set<String> otherKeys = conditionKeys(triggerService.getConditions(other.getId()));
            if (otherKeys.equals(candidateKeys)) {
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("id", other.getId());
                m.put("query", other.getQuery());
                m.put("type", other.getType());
                m.put("priority", other.getPriority());
                matches.add(m);
            }
        }
        return matches;
    }

    private java.util.Set<String> conditionKeys(
            List<com.nexarank.api.model.RuleTriggerCondition> conditions) {
        if (conditions == null) return java.util.Set.of();
        return conditions.stream()
                .map(c -> c.getFacetField() + "=" + (c.getFacetValues() == null ? "" :
                        c.getFacetValues().stream().sorted().collect(Collectors.joining(","))))
                .collect(Collectors.toSet());
    }

    
    public List<MerchRule> getRulesByQueryAndFacets(String query,
                                                     java.util.Map<String, String> selectedFacets) {
        Instant now      = Instant.now();
        String tenantId  = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();
        boolean isWildcard = query == null || query.isBlank() || query.equals("*");

        List<MerchRule> allRules = repository.findByTenantIdAndProjectId(tenantId, projectId);
        List<MerchRule> result = allRules.stream()
                .filter(r -> r.getStatus() == MerchRule.RuleStatus.LIVE && r.isEnabled())
                .filter(r -> r.getActivateAt() == null || r.getActivateAt().isBefore(now))
                .filter(r -> r.getExpireAt() == null || r.getExpireAt().isAfter(now))
                .filter(r -> {
                    // NR-128: a wildcard/blank query only satisfies requireQuery=false
                    // rules (ADR-008 — those already match unconditionally via the
                    // first clause). A requireQuery=true rule must still no-op on a
                    // blank/browse query — it was previously being force-matched by
                    // `isWildcard` here too, which never mattered while the controller
                    // hard-rejected blank queries, but would have wrongly fired every
                    // requireQuery=true rule on real browse traffic once that
                    // controller-level rejection was removed.
                    boolean queryMatches = !r.isRequireQuery()
                            || (!isWildcard && query != null && containsRuleQuery(query, r.getQuery()));
                    boolean condMatch = triggerService.conditionsMatch(r.getId(),
                            selectedFacets != null ? selectedFacets : java.util.Map.of());
                    if (!queryMatches) return false;
                    return condMatch;
                })
                .toList();
        result.forEach(this::deserializeTransientFields);
        return result;
    }

        /**
     * Direct save — bypasses approval workflow.
     * Only used internally (A/B test winner promotion, archival).
     */
    public MerchRule saveDirectly(MerchRule rule) {
        rule.setUpdatedAt(java.time.Instant.now());
        MerchRule saved = repository.save(rule);
        cacheVersionService.bump(saved.getTenantId(), saved.getProjectId());
        return saved;
    }

    /**
     * NR-36: increments fired_count/last_fired_at for rules that actually
     * produced an instruction (boost/pin/bury/synonym) on this request.
     */
    @org.springframework.transaction.annotation.Transactional
    public void recordFired(List<String> ruleIds) {
        if (ruleIds == null || ruleIds.isEmpty()) return;
        repository.incrementFiredCount(ruleIds, Instant.now());
    }

    private void deserializeTransientFields(MerchRule rule) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            if (rule.getPinnedIdsJson() != null && !rule.getPinnedIdsJson().isBlank()) {
                rule.setPinnedIds(mapper.readValue(rule.getPinnedIdsJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {}));
            }
            if (rule.getSynonymsJson() != null && !rule.getSynonymsJson().isBlank()) {
                rule.setSynonyms(mapper.readValue(rule.getSynonymsJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {}));
            }
        } catch (Exception e) {
            log.warn("Failed to deserialize transient fields for rule {}: {}", rule.getId(), e.getMessage());
        }
    }

    private void serializeTransientFields(MerchRule rule) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            if (rule.getPinnedIds() != null && !rule.getPinnedIds().isEmpty()) {
                rule.setPinnedIdsJson(mapper.writeValueAsString(rule.getPinnedIds()));
            }
            if (rule.getSynonyms() != null && !rule.getSynonyms().isEmpty()) {
                rule.setSynonymsJson(mapper.writeValueAsString(rule.getSynonyms()));
            }
        } catch (Exception e) {
            log.warn("Failed to serialize transient fields for rule {}: {}", rule.getId(), e.getMessage());
        }
    }

    /** NR-88: REDIRECT rules are useless without a real destination — enforced server-side since this is the only path both the UI and any direct API caller go through. */
    private void validateRedirectUrl(MerchRule rule) {
        if (rule.getType() != MerchRule.RuleType.REDIRECT) return;
        String url = rule.getRedirectUrl();
        if (url == null || url.isBlank() || !(url.startsWith("/") || url.startsWith("https://"))) {
            throw new IllegalArgumentException("redirectUrl is required for REDIRECT rules and must start with / or https://");
        }
    }

    private String getCurrentUsername() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
    private boolean containsRuleQuery(String query, String ruleQuery) {
        if (query == null || ruleQuery == null) return false;
        if (query.equalsIgnoreCase(ruleQuery)) return true;
        // Check if rule query appears as a whole word within the (stopword-cleaned) query
        String pattern = "(?i)(^|\\s)" + java.util.regex.Pattern.quote(ruleQuery) + "(\\s|$)";
        return java.util.regex.Pattern.compile(pattern).matcher(query).find();
    }
}
