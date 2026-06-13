// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.model.LlmConfig;
import com.nexarank.api.service.LlmConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * CRUD API for LLM provider configuration per tenant/project.
 *
 * GET  /api/v1/llm-config         — get current config
 * POST /api/v1/llm-config         — save/update config
 * POST /api/v1/llm-config/test    — test connection
 */
@RestController
@RequestMapping("/api/v1/llm-config")
public class LlmConfigController {

    private final LlmConfigService service;

    public LlmConfigController(LlmConfigService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<?> getConfig() {
        return service.getConfig()
            .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping
    public ResponseEntity<LlmConfig> saveConfig(@RequestBody LlmConfig config) {
        return ResponseEntity.ok(service.saveConfig(config));
    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testConnection(
            @RequestParam(required = false) String configId) {
        LlmConfigService.TestResult result = service.testConnection(configId);
        return ResponseEntity.ok(Map.of(
            "success", result.success(),
            "message", result.message()
        ));
    }
}
