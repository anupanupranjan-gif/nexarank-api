// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;
import com.nexarank.api.security.TenantContext;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.util.UUID;

import com.nexarank.api.model.MerchRule;
import com.nexarank.api.repository.MerchRuleRepository;
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

    public MerchRuleService(MerchRuleRepository repository) {
        this.repository = repository;
    }

    public List<MerchRule> getAllRules() {
        return repository.findByTenantIdAndProjectId(
                TenantContext.getTenantId(), TenantContext.getProjectId());
    }

    public List<MerchRule> getPendingRules() {
        return getAllRules().stream()
                .filter(r -> r.getStatus() == MerchRule.RuleStatus.PENDING_REVIEW)
                .toList();
    }

    public List<MerchRule> getApprovedRules() {
        Instant now = Instant.now();
        return getAllRules().stream()
                .filter(r -> r.getStatus() == MerchRule.RuleStatus.APPROVED && r.isEnabled())
                .filter(r -> r.getActivateAt() == null || r.getActivateAt().isBefore(now))
                .filter(r -> r.getExpireAt() == null || r.getExpireAt().isAfter(now))
                .toList();
    }

    public List<MerchRule> getRulesByQuery(String query) {
        return getApprovedRules().stream()
                .filter(r -> query.equalsIgnoreCase(r.getQuery()))
                .toList();
    }

    public MerchRule createRule(MerchRule rule) {
        if (rule.getId() == null) rule.setId(UUID.randomUUID().toString());
        if (rule.getTenantId() == null) rule.setTenantId(TenantContext.getTenantId());
        if (rule.getProjectId() == null) rule.setProjectId(TenantContext.getProjectId());
        String currentUser = getCurrentUsername();
        rule.setSubmittedBy(currentUser);
        rule.setStatus(MerchRule.RuleStatus.PENDING_REVIEW);
        rule.setEnabled(false);
        rule.setCreatedAt(Instant.now());
        rule.setUpdatedAt(Instant.now());
        MerchRule saved = repository.save(rule);
        log.info("RULE_CREATED type={} query={} by={}", rule.getType(), rule.getQuery(), rule.getSubmittedBy());
        return saved;
    }

    public Optional<MerchRule> getById(String id) {
        return repository.findById(id);
    }

    public Optional<MerchRule> updateRule(String id, MerchRule updated) {
        return repository.findById(id).map(existing -> {
            updated.setId(existing.getId());
            updated.setSubmittedBy(existing.getSubmittedBy());
            updated.setStatus(MerchRule.RuleStatus.PENDING_REVIEW);
            updated.setEnabled(false);
            updated.setCreatedAt(existing.getCreatedAt());
            updated.setUpdatedAt(Instant.now());
            return repository.save(updated);
        });
    }

    public Optional<MerchRule> approveRule(String id, String comment) {
        String currentUser = getCurrentUsername();
        return repository.findById(id).map(rule -> {
            rule.setStatus(MerchRule.RuleStatus.APPROVED);
            rule.setEnabled(true);
            rule.setApprovedBy(currentUser);
            rule.setReviewComment(comment);
            rule.setUpdatedAt(Instant.now());
            MerchRule saved = repository.save(rule);
            log.info("RULE_APPROVED id={} query={} by={} comment={}", rule.getId(), rule.getQuery(), rule.getApprovedBy(), comment);
            return saved;
        });
    }

    public Optional<MerchRule> rejectRule(String id, String comment) {
        String currentUser = getCurrentUsername();
        return repository.findById(id).map(rule -> {
            rule.setStatus(MerchRule.RuleStatus.REJECTED);
            rule.setEnabled(false);
            rule.setApprovedBy(currentUser);
            rule.setReviewComment(comment);
            rule.setUpdatedAt(Instant.now());
            MerchRule saved = repository.save(rule);
            log.info("RULE_REJECTED id={} query={} by={} comment={}", rule.getId(), rule.getQuery(), rule.getApprovedBy(), comment);
            return saved;
        });
    }

    public Optional<MerchRule> toggleRule(String id) {
        return repository.findById(id).map(rule -> {
            rule.setEnabled(!rule.isEnabled());
            rule.setUpdatedAt(Instant.now());
            return repository.save(rule);
        });
    }

    public void deleteRule(String id) {
        repository.deleteById(id);
    }

    public List<Map<String, Object>> detectConflicts(String query) {
        String tenantId = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();
        List<MerchRule> rules = repository.findByTenantIdAndProjectIdAndQueryAndEnabled(
                tenantId, projectId, query, true);

        List<Map<String, Object>> conflicts = new ArrayList<>();

        // Check conflicts between rule types
        boolean hasPin = rules.stream().anyMatch(r -> r.getType() == MerchRule.RuleType.PIN &&
                r.getStatus() == MerchRule.RuleStatus.APPROVED);
        boolean hasBoost = rules.stream().anyMatch(r -> r.getType() == MerchRule.RuleType.BOOST &&
                r.getStatus() == MerchRule.RuleStatus.APPROVED);
        boolean hasBury = rules.stream().anyMatch(r -> r.getType() == MerchRule.RuleType.BURY &&
                r.getStatus() == MerchRule.RuleStatus.APPROVED);

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

        // Check duplicate rule types
        rules.stream()
            .filter(r -> r.getStatus() == MerchRule.RuleStatus.APPROVED)
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

        // Get existing active rules for this query
        List<MerchRule> existing = repository.findByTenantIdAndProjectIdAndQueryAndEnabled(
                tenantId, projectId, rule.getQuery(), true)
                .stream()
                .filter(r -> r.getStatus() == MerchRule.RuleStatus.APPROVED)
                .sorted(java.util.Comparator.comparingInt(MerchRule::getPriority))
                .collect(java.util.stream.Collectors.toList());

        List<Map<String, Object>> conflicts = detectConflicts(rule.getQuery());

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
        preview.put("willApply", rule.getStatus() == null || rule.getStatus() != MerchRule.RuleStatus.REJECTED);

        return preview;
    }

    private String getCurrentUsername() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
