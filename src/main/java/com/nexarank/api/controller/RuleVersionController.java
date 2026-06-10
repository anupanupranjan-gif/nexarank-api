// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.model.MerchRule;
import com.nexarank.api.service.MerchRuleService;
import com.nexarank.api.service.RuleVersionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Phase 22 / NR-33: version history + rollback.
 *
 *   GET  /api/v1/rules/{id}/history             — version metadata list
 *   GET  /api/v1/rules/{id}/history/{version}   — full rule state at a version
 *   POST /api/v1/rules/{id}/rollback/{version}  — restore old values (appends a new version)
 */
@RestController
@RequestMapping("/api/v1/rules")
public class RuleVersionController {

    private final RuleVersionService versionService;
    private final MerchRuleService ruleService;

    public RuleVersionController(RuleVersionService versionService, MerchRuleService ruleService) {
        this.versionService = versionService;
        this.ruleService = ruleService;
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<Map<String, Object>>> getHistory(@PathVariable String id) {
        return ResponseEntity.ok(versionService.getHistory(id));
    }

    @GetMapping("/{id}/history/{version}")
    public ResponseEntity<MerchRule> getVersionState(@PathVariable String id, @PathVariable int version) {
        return versionService.getVersionState(id, version)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/rollback/{version}")
    public ResponseEntity<MerchRule> rollback(@PathVariable String id, @PathVariable int version) {
        return ruleService.rollbackRule(id, version)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
