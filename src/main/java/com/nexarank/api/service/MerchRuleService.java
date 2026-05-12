// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.nexarank.api.model.MerchRule;
import com.nexarank.api.repository.MerchRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

    public List<MerchRule> getRulesForQuery(String query) {
        return repository.findByQueryAndEnabled(query, true);
    }

    public MerchRule createRule(MerchRule rule) {
        rule.setId(UUID.randomUUID().toString());
        rule.setEnabled(true);
        rule.setCreatedAt(Instant.now());
        rule.setUpdatedAt(Instant.now());
        log.info("Creating rule type={} query={}", rule.getType(), rule.getQuery());
        return repository.save(rule);
    }

    public Optional<MerchRule> updateRule(String id, MerchRule updated) {
        return repository.findById(id).map(existing -> {
            updated.setId(id);
            updated.setCreatedAt(existing.getCreatedAt());
            updated.setUpdatedAt(Instant.now());
            return repository.save(updated);
        });
    }

    public void deleteRule(String id) {
        repository.deleteById(id);
        log.info("Deleted rule id={}", id);
    }

    public void toggleRule(String id, boolean enabled) {
        repository.findById(id).ifPresent(rule -> {
            rule.setEnabled(enabled);
            rule.setUpdatedAt(Instant.now());
            repository.save(rule);
        });
    }
}
