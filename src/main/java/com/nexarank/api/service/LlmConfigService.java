// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarank.api.adapter.LlmAdapterFactory;
import com.nexarank.api.model.LlmConfig;
import com.nexarank.api.port.LlmPort;
import com.nexarank.api.repository.LlmConfigRepository;
import com.nexarank.api.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class LlmConfigService {

    private static final Logger log = LoggerFactory.getLogger(LlmConfigService.class);

    private final LlmConfigRepository repository;
    private final LlmAdapterFactory adapterFactory;
    private final ObjectMapper objectMapper;

    public LlmConfigService(LlmConfigRepository repository,
                             LlmAdapterFactory adapterFactory,
                             ObjectMapper objectMapper) {
        this.repository     = repository;
        this.adapterFactory = adapterFactory;
        this.objectMapper   = objectMapper;
    }

    /**
     * Get LLM config for current tenant/project.
     * Used by LlmQueryRewriteStage and other LLM pipeline stages.
     */
    public Optional<LlmConfig> getConfig() {
        return repository.findFirstByTenantIdAndProjectId(
                TenantContext.getTenantId(), TenantContext.getProjectId())
            .map(this::withDeserializedFields);
    }

    public LlmConfig saveConfig(LlmConfig config) {
        // One config per tenant/project
        getConfig().ifPresent(existing -> config.setId(existing.getId()));
        if (config.getId() == null) config.setId(UUID.randomUUID().toString());
        if (config.getTenantId() == null) config.setTenantId(TenantContext.getTenantId());
        if (config.getProjectId() == null) config.setProjectId(TenantContext.getProjectId());
        if (config.getCreatedAt() == null) config.setCreatedAt(Instant.now());
        config.setUpdatedAt(Instant.now());
        config.setLastStatus(LlmConfig.ConnectionStatus.UNTESTED);
        serializeTransientFields(config);

        LlmConfig saved = repository.save(config);
        log.info("LLM config saved: provider={} model={} endpoint={}",
            config.getProvider(), config.getModel(), config.getEndpoint());
        return withDeserializedFields(saved);
    }

    // ── NR-176: JSON <-> transient field helpers (promptTemplatesJson/promptTemplates) ──

    private LlmConfig withDeserializedFields(LlmConfig config) {
        try {
            if (config.getPromptTemplatesJson() != null && !config.getPromptTemplatesJson().isBlank()) {
                config.setPromptTemplates(objectMapper.readValue(config.getPromptTemplatesJson(),
                        new TypeReference<Map<String, String>>() {}));
            }
        } catch (Exception e) {
            log.warn("Failed to deserialize prompt templates for LLM config {}: {}", config.getId(), e.getMessage());
        }
        return config;
    }

    private void serializeTransientFields(LlmConfig config) {
        try {
            if (config.getPromptTemplates() != null) {
                config.setPromptTemplatesJson(objectMapper.writeValueAsString(config.getPromptTemplates()));
            }
        } catch (Exception e) {
            log.warn("Failed to serialize prompt templates for LLM config {}: {}", config.getId(), e.getMessage());
        }
    }

    public TestResult testConnection(String configId) {
        Optional<LlmConfig> opt = configId != null
            ? repository.findById(configId)
            : getConfig();

        if (opt.isEmpty()) {
            return new TestResult(false, "No LLM config found. Please save a config first.");
        }

        LlmConfig config = opt.get();
        try {
            LlmPort adapter = adapterFactory.getAdapter(config);
            boolean ok = adapter.testConnection(config);

            config.setLastStatus(ok
                ? LlmConfig.ConnectionStatus.CONNECTED
                : LlmConfig.ConnectionStatus.FAILED);
            config.setLastStatusMessage(ok
                ? "Connected to " + config.getProvider() + " — model: " + config.getModel()
                : "Failed to connect to " + config.getEndpoint());
            config.setLastTestedAt(Instant.now());
            repository.save(config);

            return new TestResult(ok, config.getLastStatusMessage());

        } catch (Exception e) {
            config.setLastStatus(LlmConfig.ConnectionStatus.FAILED);
            config.setLastStatusMessage(e.getMessage());
            config.setLastTestedAt(Instant.now());
            repository.save(config);
            return new TestResult(false, "Error: " + e.getMessage());
        }
    }

    public record TestResult(boolean success, String message) {}
}
