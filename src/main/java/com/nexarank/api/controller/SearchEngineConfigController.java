// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.model.SearchEngineConfig;
import com.nexarank.api.model.SearchField;
import com.nexarank.api.service.SearchEngineConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/engine-config")
public class SearchEngineConfigController {

    private final SearchEngineConfigService service;

    public SearchEngineConfigController(SearchEngineConfigService service) {
        this.service = service;
    }

    /**
     * GET current search engine config.
     * Used by NexaRank admin UI to show current connection settings.
     */
    @GetMapping
    public ResponseEntity<?> getConfig() {
        return service.getConfig()
            .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElse(ResponseEntity.noContent().build());
    }

    /**
     * POST save/update search engine config.
     * Admin configures host, port, credentials, engine type.
     */
    @PostMapping
    public ResponseEntity<SearchEngineConfig> saveConfig(
            @RequestBody SearchEngineConfig config) {
        return ResponseEntity.ok(service.saveConfig(config));
    }

    /**
     * POST test connection with current config.
     * Returns {success, message} — used by "Test Connection" button in UI.
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testConnection(
            @RequestParam(required = false) String configId) {
        SearchEngineConfigService.TestResult result = service.testConnection(configId);
        return ResponseEntity.ok(Map.of(
            "success", result.success(),
            "message", result.message()
        ));
    }

    /**
     * GET fields from the connected search engine.
     * Used in NexaRank admin UI for:
     * - Facet config field dropdowns
     * - Rule boost/bury field dropdowns
     * - Schema explorer
     */
    @GetMapping("/fields")
    public ResponseEntity<List<SearchField>> getFields() {
        List<SearchField> fields = service.getFields();
        if (fields.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(fields);
    }

    /**
     * GET sample values for a specific field.
     * Used in rule creation UI for value dropdowns.
     * e.g. GET /fields/brand/values → ["Duracell", "Bosch", "Mobil 1"]
     */
    @GetMapping("/fields/{fieldName}/values")
    public ResponseEntity<List<String>> getFieldValues(
            @PathVariable String fieldName) {
        List<String> values = service.getFieldValues(fieldName);
        return ResponseEntity.ok(values);
    }
}
