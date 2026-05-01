package com.nexarank.api.service;

import com.nexarank.api.model.MerchRule;
import com.nexarank.api.repository.MerchRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MerchRuleService {

    private static final Logger log = LoggerFactory.getLogger(MerchRuleService.class);
    private final MerchRuleRepository repository;

    public MerchRuleService(MerchRuleRepository repository) {
        this.repository = repository;
    }

    public List<MerchRule> getAllRules() {
        return (List<MerchRule>) repository.findAll();
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
