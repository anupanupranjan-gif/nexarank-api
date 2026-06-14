// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.nexarank.api.model.SuggestionConfig;
import com.nexarank.api.repository.SuggestionConfigRepository;
import com.nexarank.api.security.TenantContext;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class SuggestionConfigService {

    private final SuggestionConfigRepository repository;

    public SuggestionConfigService(SuggestionConfigRepository repository) {
        this.repository = repository;
    }

    /**
     * Get config for current tenant/project.
     * Returns defaults if not yet configured.
     */
    public SuggestionConfig getConfig() {
        return repository.findFirstByTenantIdAndProjectId(
            TenantContext.getTenantId(), TenantContext.getProjectId())
            .orElseGet(this::defaults);
    }

    public SuggestionConfig saveConfig(SuggestionConfig config) {
        Optional<SuggestionConfig> existing = repository.findFirstByTenantIdAndProjectId(
            TenantContext.getTenantId(), TenantContext.getProjectId());

        existing.ifPresent(e -> config.setId(e.getId()));
        if (config.getId() == null) config.setId(UUID.randomUUID().toString());
        if (config.getTenantId() == null) config.setTenantId(TenantContext.getTenantId());
        if (config.getProjectId() == null) config.setProjectId(TenantContext.getProjectId());
        if (config.getCreatedAt() == null) config.setCreatedAt(Instant.now());
        config.setUpdatedAt(Instant.now());
        return repository.save(config);
    }

    private SuggestionConfig defaults() {
        SuggestionConfig config = new SuggestionConfig();
        config.setTenantId(TenantContext.getTenantId());
        config.setProjectId(TenantContext.getProjectId());
        return config;
    }
}
