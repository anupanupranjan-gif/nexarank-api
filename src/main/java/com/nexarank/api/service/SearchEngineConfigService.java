// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.nexarank.api.adapter.SearchEngineAdapterFactory;
import com.nexarank.api.model.SearchEngineConfig;
import com.nexarank.api.model.SearchField;
import com.nexarank.api.port.SearchEnginePort;
import com.nexarank.api.repository.SearchEngineConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

@Service
public class SearchEngineConfigService {

    private static final Logger log = LoggerFactory.getLogger(SearchEngineConfigService.class);

    private final SearchEngineConfigRepository repository;
    private final SearchEngineAdapterFactory adapterFactory;

    public SearchEngineConfigService(SearchEngineConfigRepository repository,
                                      SearchEngineAdapterFactory adapterFactory) {
        this.repository = repository;
        this.adapterFactory = adapterFactory;
    }

    public Optional<SearchEngineConfig> getConfig() {
        return StreamSupport
            .stream(repository.findAll(PageRequest.of(0, 1)).spliterator(), false)
            .findFirst();
    }

    public SearchEngineConfig saveConfig(SearchEngineConfig config) {
        // Only one config per NexaRank instance (single tenant for now)
        // In multi-tenant Phase 23 this becomes per-tenant
        getConfig().ifPresent(existing -> config.setId(existing.getId()));

        if (config.getCreatedAt() == null) {
            config.setCreatedAt(Instant.now());
        }
        config.setUpdatedAt(Instant.now());
        config.setLastStatus(SearchEngineConfig.ConnectionStatus.UNTESTED);

        SearchEngineConfig saved = repository.save(config);
        log.info("Search engine config saved: type={} host={}:{}",
            config.getEngineType(), config.getHost(), config.getPort());
        return saved;
    }

    public TestResult testConnection(String configId) {
        Optional<SearchEngineConfig> opt = configId != null
            ? repository.findById(configId)
            : getConfig();

        if (opt.isEmpty()) {
            return new TestResult(false, "No search engine config found. Please save a config first.");
        }

        SearchEngineConfig config = opt.get();
        try {
            SearchEnginePort adapter = adapterFactory.getAdapter(config);
            boolean ok = adapter.testConnection(config);

            config.setLastStatus(ok
                ? SearchEngineConfig.ConnectionStatus.CONNECTED
                : SearchEngineConfig.ConnectionStatus.FAILED);
            config.setLastStatusMessage(ok ? "Connection successful" : "Connection failed");
            config.setLastTestedAt(Instant.now());
            repository.save(config);

            return new TestResult(ok, ok ? "Connected successfully to " +
                config.getConnectionUrl() : "Failed to connect to " + config.getConnectionUrl());

        } catch (Exception e) {
            config.setLastStatus(SearchEngineConfig.ConnectionStatus.FAILED);
            config.setLastStatusMessage(e.getMessage());
            config.setLastTestedAt(Instant.now());
            repository.save(config);
            return new TestResult(false, "Error: " + e.getMessage());
        }
    }

    public List<SearchField> getFields() {
        return getConfig().map(config -> {
            SearchEnginePort adapter = adapterFactory.getAdapter(config);
            return adapter.getFields(config);
        }).orElse(List.of());
    }

    public List<String> getFieldValues(String fieldName) {
        return getConfig().map(config -> {
            SearchEnginePort adapter = adapterFactory.getAdapter(config);
            return adapter.getFieldValues(fieldName, config);
        }).orElse(List.of());
    }

    public record TestResult(boolean success, String message) {}
}
