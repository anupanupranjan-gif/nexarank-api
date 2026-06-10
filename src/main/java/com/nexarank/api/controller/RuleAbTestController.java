// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.model.RuleAbTest;
import com.nexarank.api.service.RuleAbTestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Phase 22 / NR-32: A/B testing endpoints.
 *
 *   GET    /api/v1/ab-tests                        — all tests for tenant/project
 *   GET    /api/v1/ab-tests/running                — running tests only
 *   POST   /api/v1/ab-tests                        — create test {ruleAId, ruleBId}
 *   GET    /api/v1/ab-tests/{id}                   — get test by id
 *   POST   /api/v1/ab-tests/{id}/promote           — promote winner {variant: "A"|"B"}
 *   POST   /api/v1/ab-tests/{id}/archive           — archive test
 *   POST   /api/v1/ab-tests/{id}/click             — record a click {variant: "A"|"B"}
 */
@RestController
@RequestMapping("/api/v1/ab-tests")
public class RuleAbTestController {

    private final RuleAbTestService abTestService;

    public RuleAbTestController(RuleAbTestService abTestService) {
        this.abTestService = abTestService;
    }

    @GetMapping
    public ResponseEntity<List<RuleAbTest>> getAllTests() {
        return ResponseEntity.ok(abTestService.getAllTests());
    }

    @GetMapping("/running")
    public ResponseEntity<List<RuleAbTest>> getRunningTests() {
        return ResponseEntity.ok(abTestService.getRunningTests());
    }

    @PostMapping
    public ResponseEntity<?> createTest(@RequestBody Map<String, String> body) {
        String ruleAId = body.get("ruleAId");
        String ruleBId = body.get("ruleBId");
        if (ruleAId == null || ruleBId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "ruleAId and ruleBId are required"));
        }
        try {
            return ResponseEntity.ok(abTestService.createTest(ruleAId, ruleBId));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<RuleAbTest> getTest(@PathVariable String id) {
        return abTestService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/promote")
    public ResponseEntity<?> promote(@PathVariable String id,
                                     @RequestBody Map<String, String> body) {
        String variant = body.getOrDefault("variant", "A");
        if (!variant.equals("A") && !variant.equals("B")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "variant must be A or B"));
        }
        try {
            return ResponseEntity.ok(abTestService.promoteWinner(id, variant));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<?> archive(@PathVariable String id) {
        try {
            return ResponseEntity.ok(abTestService.archiveTest(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/click")
    public ResponseEntity<?> recordClick(@PathVariable String id,
                                         @RequestBody Map<String, String> body) {
        String variant = body.get("variant");
        if (variant == null || (!variant.equals("A") && !variant.equals("B"))) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "variant must be A or B"));
        }
        abTestService.recordClick(id, variant);
        return ResponseEntity.accepted().build();
    }
}
