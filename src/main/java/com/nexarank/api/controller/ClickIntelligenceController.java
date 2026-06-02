// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.model.ClickAggregate;
import com.nexarank.api.service.ClickIntelligenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/click-intelligence")
public class ClickIntelligenceController {

    private final ClickIntelligenceService service;

    public ClickIntelligenceController(ClickIntelligenceService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        return ResponseEntity.ok(service.getSummaryStats());
    }

    @GetMapping("/low-ctr")
    public ResponseEntity<List<ClickAggregate>> getLowCtrQueries(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(service.getLowCtrQueries(limit));
    }

    @GetMapping("/boost-candidates")
    public ResponseEntity<List<ClickAggregate>> getBoostCandidates(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(service.getBoostCandidates(limit));
    }

    @GetMapping("/top-clicked")
    public ResponseEntity<List<ClickAggregate>> getTopClicked(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(service.getTopClickedForQuery(query, limit));
    }
}
