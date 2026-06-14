// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.model.SuggestionConfig;
import com.nexarank.api.service.SuggestionConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/suggestions/config")
public class SuggestionConfigController {

    private final SuggestionConfigService service;

    public SuggestionConfigController(SuggestionConfigService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<SuggestionConfig> getConfig() {
        return ResponseEntity.ok(service.getConfig());
    }

    @PostMapping
    public ResponseEntity<SuggestionConfig> saveConfig(@RequestBody SuggestionConfig config) {
        return ResponseEntity.ok(service.saveConfig(config));
    }
}
