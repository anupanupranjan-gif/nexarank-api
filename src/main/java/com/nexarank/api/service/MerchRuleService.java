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
    private final AuditDiffService auditDiffService;
    private final RulesCacheVersionService cacheVersionService;

    public MerchRuleService(MerchRuleRepository repository,
                             RuleVersionService versionService,
                             RuleTriggerConditionService triggerService,
                             RuleNotificationService notificationService,
                             TenantRepository tenantRepository,
                             AuditService auditService,
                             AuditDiffService auditDiffService,
                             RulesCacheVersionService cacheVersionService) {
        this.repository     = repository;
        this.versionService = versionService;
        this.triggerService = triggerService;
        this.notificationService = notificationService;
        this.tenantRepository = tenantRepository;
        this.auditService = auditService;
        this.auditDiffService = auditDiffService;
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
        auditService.logRuleChange("RULE_CREATED", null, saved, null, currentUser);
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

    public record ImportResult(MerchRule rule, boolean idCollisionResolved) {}

    /**
     * NR-167 (config import): upsert-by-id. Matches the exported item's `id`
     * ONLY within the CURRENT tenant+project — an id that exists but belongs
     * to a DIFFERENT tenant/project (e.g. the same export bundle already
     * imported into another environment sharing this database) is NOT
     * treated as a match. Reusing/mutating another tenant's row by id would
     * reopen exactly the cross-tenant IDOR class of bug fixed in NR-162, so
     * in that case a fresh id is generated instead and the caller is told
     * via idCollisionResolved so it can be surfaced in the import summary.
     *
     * Imported rules land APPROVED — never DRAFT — per explicit decision
     * (2026-08-25): re-approving every rule on every import wastes review
     * time for what's meant to be a fast disaster-recovery/migration path.
     * If the target tenant has auto-publish on, this cascades straight to
     * LIVE the same way a normal approval would (doPromoteToLive below).
     */
    @org.springframework.transaction.annotation.Transactional
    public ImportResult importRule(com.nexarank.api.configexport.dto.RuleExport dto) {
        String tenantId = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();
        String actor = getCurrentUsername();

        Optional<MerchRule> existingAnywhere = repository.findById(dto.id());
        boolean idCollision = existingAnywhere.isPresent() &&
                !(tenantId.equals(existingAnywhere.get().getTenantId())
                        && projectId.equals(existingAnywhere.get().getProjectId()));

        MerchRule rule;
        boolean isNew;
        MerchRule beforeState = null;
        if (existingAnywhere.isPresent() && !idCollision) {
            rule = existingAnywhere.get();
            deserializeTransientFields(rule);
            beforeState = auditDiffService.copyOf(rule);
            isNew = false;
        } else {
            rule = new MerchRule();
            rule.setId(idCollision ? UUID.randomUUID().toString() : dto.id());
            rule.setTenantId(tenantId);
            rule.setProjectId(projectId);
            rule.setCreatedAt(Instant.now());
            isNew = true;
        }

        rule.setType(MerchRule.RuleType.valueOf(dto.type()));
        rule.setQuery(dto.query());
        rule.setBoostField(dto.boostField());
        rule.setBoostValue(dto.boostValue());
        rule.setBoostFactor(dto.boostFactor());
        rule.setPinnedIds(dto.pinnedIds());
        rule.setSynonyms(dto.synonyms());
        rule.setSynonymDirection(dto.synonymDirection() == null
                ? MerchRule.SynonymDirection.TWO_WAY : MerchRule.SynonymDirection.valueOf(dto.synonymDirection()));
        rule.setPriority(dto.priority());
        rule.setRequireQuery(dto.requireQuery());
        rule.setRedirectUrl(dto.redirectUrl());
        rule.setActivateAt(dto.activateAt());
        rule.setExpireAt(dto.expireAt());
        rule.setSubmittedBy(actor);
        rule.setStatus(MerchRule.RuleStatus.APPROVED);
        rule.setApprovedBy("import");
        rule.setEnabled(false);
        rule.setUpdatedAt(Instant.now());
        serializeTransientFields(rule);

        MerchRule saved = repository.save(rule);
        versionService.snapshot(saved, actor, isNew ? "Imported" : "Re-imported (updated from source)");
        auditService.logRuleChange(isNew ? "RULE_IMPORTED" : "RULE_REIMPORTED", beforeState, saved, null, actor);

        List<Map<String, Object>> conditionDtos = dto.triggerConditions() == null ? List.of()
                : dto.triggerConditions().stream()
                        .map(c -> { Map<String, Object> d = new LinkedHashMap<>();
                            d.put("facetField", c.facetField());
                            d.put("facetValues", c.facetValues());
                            return d; })
                        .toList();
        triggerService.saveConditions(saved.getId(), conditionDtos);

        boolean autoPublish = tenantRepository.findById(saved.getTenantId())
                .map(Tenant::isAutoPublishRules).orElse(true);
        if (autoPublish) {
            saved = doPromoteToLive(saved, "import", "Auto-published on import (tenant auto-publish enabled)");
        }
        cacheVersionService.bump(saved.getTenantId(), saved.getProjectId());
        log.info("RULE_IMPORTED id={} query={} isNew={} idCollisionResolved={} autoPublish={}",
                saved.getId(), saved.getQuery(), isNew, idCollision, autoPublish);
        return new ImportResult(saved, idCollision);
    }

    public Optional<MerchRule> getById(String id) {
        return findScopedById(id).map(rule -> {
            rule.setTriggerConditions(triggerService.getConditions(id));
            deserializeTransientFields(rule);
            return rule;
        });
    }

    /**
     * NR-162 fix: every by-ID entry point below must go through this instead
     * of repository.findById() directly. A plain findById(id) has no
     * tenant/project filter at all — since rule ids are random UUIDs, any
     * caller who knew or guessed an id could read or mutate a rule belonging
     * to a different project (or a different tenant entirely), regardless of
     * the caller's own project-scoped role. Rules outside the caller's
     * current tenant+project context are treated as not found, same as a
     * genuinely missing id, so this doesn't leak existence either.
     */
    private Optional<MerchRule> findScopedById(String id) {
        return repository.findById(id)
                .filter(r -> java.util.Objects.equals(r.getTenantId(), TenantContext.getTenantId())
                        && java.util.Objects.equals(r.getProjectId(), TenantContext.getProjectId()));
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
        return findScopedById(id).map(existing -> {
            // NR-70: detached snapshot taken before any mutation, so the audit
            // diff compares real before/after state rather than one object with
            // itself. Transient fields are deserialized first, otherwise
            // synonyms/pinnedIds would read as null on the "before" side and
            // every edit would look like it set them for the first time.
            deserializeTransientFields(existing);
            MerchRule beforeState = auditDiffService.copyOf(existing);
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
            auditService.logRuleChange("RULE_UPDATED", beforeState, saved, null, getCurrentUsername());
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
        return findScopedById(id).map(rule -> {
            if (rule.getStatus() != MerchRule.RuleStatus.DRAFT) {
                throw new IllegalArgumentException(
                        "Only DRAFT rules can be submitted for review (current status: " + rule.getStatus() + ")");
            }
            MerchRule beforeState = auditDiffService.copyOf(rule);
            rule.setStatus(MerchRule.RuleStatus.PENDING_REVIEW);
            rule.setUpdatedAt(Instant.now());
            MerchRule saved = repository.save(rule);
            versionService.snapshot(saved, currentUser, "Submitted for review");
            auditService.logRuleChange("RULE_SUBMITTED", beforeState, saved, null, currentUser);
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
        return findScopedById(id).map(rule -> {
            MerchRule beforeState = auditDiffService.copyOf(rule);
            rule.setStatus(MerchRule.RuleStatus.APPROVED);
            rule.setApprovedBy(currentUser);
            rule.setReviewComment(comment);
            rule.setUpdatedAt(Instant.now());
            MerchRule saved = repository.save(rule);
            auditService.logRuleChange("RULE_APPROVED", beforeState, saved, comment, currentUser);
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
        return findScopedById(id).map(rule -> {
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
        return findScopedById(id).map(rule -> {
            if (rule.getStatus() != MerchRule.RuleStatus.LIVE) {
                throw new IllegalArgumentException(
                        "Only LIVE rules can be demoted (current status: " + rule.getStatus() + ")");
            }
            MerchRule beforeState = auditDiffService.copyOf(rule);
            rule.setStatus(target);
            rule.setEnabled(false);
            rule.setUpdatedAt(Instant.now());
            MerchRule saved = repository.save(rule);
            versionService.snapshot(saved, currentUser, "Reverted from live to " + target);
            auditService.logRuleChange("RULE_DEMOTED", beforeState, saved, null, currentUser);
            log.info("RULE_DEMOTED id={} query={} to={} by={}", rule.getId(), rule.getQuery(), target, currentUser);
            cacheVersionService.bump(saved.getTenantId(), saved.getProjectId());
            return saved;
        });
    }

    private MerchRule doPromoteToLive(MerchRule rule, String actor, String versionNote) {
        MerchRule beforeState = auditDiffService.copyOf(rule);
        rule.setStatus(MerchRule.RuleStatus.LIVE);
        rule.setEnabled(true);
        rule.setUpdatedAt(Instant.now());
        MerchRule saved = repository.save(rule);
        versionService.snapshot(saved, actor, versionNote);
        // Single choke point for every APPROVED -> LIVE path (auto-publish,
        // approve-and-publish, and manual promote), so none can bypass the audit.
        auditService.logRuleChange("RULE_PROMOTED_LIVE", beforeState, saved, versionNote, actor);
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
        return findScopedById(id).map(rule -> {
            MerchRule beforeState = auditDiffService.copyOf(rule);
            rule.setStatus(MerchRule.RuleStatus.DRAFT);
            rule.setEnabled(false);
            rule.setApprovedBy(currentUser);
            rule.setReviewComment(comment);
            rule.setUpdatedAt(Instant.now());
            MerchRule saved = repository.save(rule);
            auditService.logRuleChange("RULE_REJECTED", beforeState, saved, comment, currentUser);
            versionService.snapshot(saved, currentUser,
                    comment == null || comment.isBlank() ? "Rule rejected" : "Rule rejected: " + comment);
            log.info("RULE_REJECTED id={} query={} by={} comment={}", rule.getId(), rule.getQuery(), rule.getApprovedBy(), comment);
            cacheVersionService.bump(saved.getTenantId(), saved.getProjectId());
            notificationService.notifyRejected(saved, comment);
            return saved;
        });
    }

    public Optional<MerchRule> toggleRule(String id) {
        return findScopedById(id).map(rule -> {
            MerchRule beforeState = auditDiffService.copyOf(rule);
            rule.setEnabled(!rule.isEnabled());
            rule.setUpdatedAt(Instant.now());
            MerchRule saved = repository.save(rule);
            auditService.logRuleChange("RULE_TOGGLED", beforeState, saved, null, getCurrentUsername());
            cacheVersionService.bump(saved.getTenantId(), saved.getProjectId());
            return saved;
        });
    }

    public void deleteRule(String id) {
        // NR-162 fix: deletion used to run unconditionally regardless of
        // whether the rule was even found in-scope, so a cross-project id
        // still got deleted even though the "logged" branch silently
        // no-opped for it. Now the delete itself is scoped too.
        findScopedById(id).ifPresent(rule -> {
            // Logged before the row is gone. entity_name is denormalized at
            // write time, so this entry stays readable after the rule itself
            // no longer exists to join against.
            auditService.logRuleChange("RULE_DELETED", rule, null, null, getCurrentUsername());
            cacheVersionService.bump(rule.getTenantId(), rule.getProjectId());
            repository.deleteById(id);
        });
    }

    public Optional<MerchRule> rollbackRule(String id, int versionNumber) {
        String currentUser = getCurrentUsername();
        return findScopedById(id)
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
