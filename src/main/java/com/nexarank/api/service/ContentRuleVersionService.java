// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarank.api.model.ContentRule;
import com.nexarank.api.model.ContentRuleVersion;
import com.nexarank.api.repository.ContentRuleVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * NR-84: append-only version history for ContentRule, same pattern as
 * RuleVersionService for MerchRule. No rollback here — not requested by
 * NR-84 (only "every save creates a new version record").
 */
@Service
public class ContentRuleVersionService {

    private static final Logger log = LoggerFactory.getLogger(ContentRuleVersionService.class);

    private final ContentRuleVersionRepository repository;
    private final ObjectMapper objectMapper;

    public ContentRuleVersionService(ContentRuleVersionRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ContentRuleVersion snapshot(ContentRule rule, String changedBy, String changeNote) {
        int nextVersion = repository.findTopByRuleIdOrderByVersionNumberDesc(rule.getId())
                .map(v -> v.getVersionNumber() + 1)
                .orElse(1);

        ContentRuleVersion version = new ContentRuleVersion();
        version.setId(UUID.randomUUID().toString());
        version.setRuleId(rule.getId());
        version.setTenantId(rule.getTenantId());
        version.setVersionNumber(nextVersion);
        version.setSnapshot(serialize(rule));
        version.setChangedBy(changedBy);
        version.setChangedAt(Instant.now());
        version.setChangeNote(changeNote);

        ContentRuleVersion saved = repository.save(version);
        log.info("CONTENT_RULE_VERSION_CREATED ruleId={} version={} by={} note={}",
                rule.getId(), nextVersion, changedBy, changeNote);
        return saved;
    }

    public List<Map<String, Object>> getHistory(String ruleId) {
        return repository.findByRuleIdOrderByVersionNumberDesc(ruleId).stream()
                .map(v -> Map.<String, Object>of(
                        "versionNumber", v.getVersionNumber(),
                        "changedBy", v.getChangedBy() == null ? "system" : v.getChangedBy(),
                        "changedAt", v.getChangedAt(),
                        "changeNote", v.getChangeNote() == null ? "" : v.getChangeNote()))
                .collect(Collectors.toList());
    }

    private String serialize(ContentRule rule) {
        try {
            return objectMapper.writeValueAsString(rule);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize content rule snapshot for " + rule.getId(), e);
        }
    }
}
