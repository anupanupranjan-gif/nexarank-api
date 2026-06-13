// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.model.FacetConfig;
import com.nexarank.api.model.FacetVisibilityRule;
import com.nexarank.api.service.FacetConfigService;
import com.nexarank.api.service.FacetVisibilityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Phase 24 / NR-38: Conditional facet visibility rules.
 *
 *   GET    /api/v1/facets/visibility              — list all rules
 *   POST   /api/v1/facets/visibility              — create rule
 *   PUT    /api/v1/facets/visibility/{id}         — update rule
 *   DELETE /api/v1/facets/visibility/{id}         — delete rule
 *   POST   /api/v1/facets/visibility/preview      — preview effective facets for a context
 */
@RestController
@RequestMapping("/api/v1/facets/visibility")
public class FacetVisibilityController {

    private final FacetVisibilityService visibilityService;
    private final FacetConfigService facetConfigService;

    public FacetVisibilityController(FacetVisibilityService visibilityService,
                                      FacetConfigService facetConfigService) {
        this.visibilityService = visibilityService;
        this.facetConfigService = facetConfigService;
    }

    @GetMapping
    public ResponseEntity<List<FacetVisibilityRule>> getAllRules() {
        return ResponseEntity.ok(visibilityService.getAllRules());
    }

    @PostMapping
    public ResponseEntity<?> createRule(@RequestBody FacetVisibilityRule rule) {
        if (rule.getTriggerFacetField() == null || rule.getTriggerFacetValue() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "triggerFacetField and triggerFacetValue are required"));
        }
        return ResponseEntity.ok(visibilityService.createRule(rule));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateRule(@PathVariable String id,
                                         @RequestBody FacetVisibilityRule rule) {
        return visibilityService.updateRule(id, rule)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable String id) {
        visibilityService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/preview")
    public ResponseEntity<Map<String, Object>> preview(
            @RequestBody Map<String, String> body) {
        String field = body.get("triggerFacetField");
        String value = body.get("triggerFacetValue");
        if (field == null || value == null) {
            return ResponseEntity.badRequest().build();
        }
        List<FacetConfig> allFacets = facetConfigService.getAllFacets();
        return ResponseEntity.ok(visibilityService.previewVisibility(allFacets, field, value));
    }
}
