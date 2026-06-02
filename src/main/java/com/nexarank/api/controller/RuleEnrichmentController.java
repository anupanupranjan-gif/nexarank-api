// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.model.EnrichedQuery;
import com.nexarank.api.service.RuleEnrichmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/rules")
public class RuleEnrichmentController {

    private final RuleEnrichmentService enrichmentService;

    public RuleEnrichmentController(RuleEnrichmentService enrichmentService) {
        this.enrichmentService = enrichmentService;
    }

    /**
     * POST /api/v1/rules/enrich
     *
     * The core NexaRank integration endpoint.
     * Customer's search service calls this on every search request.
     *
     * Request:
     * {
     *   "query": "car battery",
     *   "engineType": "ELASTICSEARCH",  // optional, uses stored config if omitted
     *   "zone": "search-results"         // optional
     * }
     *
     * Response: EnrichedQuery with engine-agnostic instructions + engineDsl
     *
     * Latency target: <20ms p99 (rules loaded from ES, no blocking calls)
     */
    @PostMapping("/enrich")
    public ResponseEntity<EnrichedQuery> enrich(
            @RequestBody Map<String, String> request) {

        String query      = request.getOrDefault("query", "");
        String engineType = request.get("engineType");
        String zone       = request.getOrDefault("zone", "search-results");

        if (query.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        EnrichedQuery result = enrichmentService.enrich(query, engineType, zone);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/v1/rules/enrich?query=...&engineType=...
     *
     * Convenience GET endpoint for testing in browser/curl.
     * Same as POST but via query params.
     */
    @GetMapping("/enrich")
    public ResponseEntity<EnrichedQuery> enrichGet(
            @RequestParam String query,
            @RequestParam(required = false) String engineType,
            @RequestParam(defaultValue = "search-results") String zone) {

        if (query.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        EnrichedQuery result = enrichmentService.enrich(query, engineType, zone);
        return ResponseEntity.ok(result);
    }
}
