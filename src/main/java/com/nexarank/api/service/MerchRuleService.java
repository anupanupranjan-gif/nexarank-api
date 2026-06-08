// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;
import java.util.UUID;

import com.nexarank.api.model.MerchRule;
import com.nexarank.api.repository.MerchRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

@Service
public class MerchRuleService {

    private static final Logger log = LoggerFactory.getLogger(MerchRuleService.class);

    private final MerchRuleRepository repository;

    public MerchRuleService(MerchRuleRepository repository) {
        this.repository = repository;
    }

    public List<MerchRule> getAllRules() {
        return StreamSupport
                .stream(repository.findAll(PageRequest.of(0, 100)).spliterator(), false)
                .toList();
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
        if (rule.getTenantId() == null) rule.setTenantId("default");
        if (rule.getProjectId() == null) rule.setProjectId("main");
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

    private String getCurrentUsername() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
