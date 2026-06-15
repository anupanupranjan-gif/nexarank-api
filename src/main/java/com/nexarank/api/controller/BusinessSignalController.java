// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.model.BusinessSignal;
import com.nexarank.api.service.BusinessSignalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Business signal ingestion and management.
 *
 * POST /api/v1/signals/ingest        — batch ingest from ERP/PIM/OMS
 * POST /api/v1/signals/seed-demo     — seed dummy signals for testing
 * GET  /api/v1/signals               — list all signals
 * GET  /api/v1/signals/active        — list currently active signals
 * DELETE /api/v1/signals/{id}        — remove a signal
 */
@RestController
@RequestMapping("/api/v1/signals")
public class BusinessSignalController {

    private final BusinessSignalService service;

    public BusinessSignalController(BusinessSignalService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<BusinessSignal>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/active")
    public ResponseEntity<List<BusinessSignal>> getActive() {
        return ResponseEntity.ok(service.getActiveSignals());
    }

    @PostMapping("/ingest")
    public ResponseEntity<Map<String, Object>> ingest(
            @RequestBody List<BusinessSignal> signals,
            @RequestParam(defaultValue = "API") String source) {
        List<BusinessSignal> saved = service.ingest(signals, source);
        return ResponseEntity.ok(Map.of(
            "ingested", saved.size(),
            "source", source
        ));
    }

    @PostMapping("/seed-demo")
    public ResponseEntity<Map<String, Object>> seedDemo(
            @RequestBody(required = false) List<String> productIds) {
        List<String> ids = productIds != null ? productIds : List.of();
        List<BusinessSignal> seeded = service.seedDemoSignals(ids);
        return ResponseEntity.ok(Map.of(
            "seeded", seeded.size(),
            "signals", seeded.stream().map(s -> Map.of(
                "productId", s.getProductId(),
                "signalType", s.getSignalType().name()
            )).toList()
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
