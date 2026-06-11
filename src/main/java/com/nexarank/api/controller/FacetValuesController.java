// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.service.FacetValuesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Phase 23 / NR-37 v2: live facet value lookup for the rule builder UI.
 *
 *   GET /api/v1/facets/values?field=category&query=battery&size=20
 *
 * Returns top values for the given facet field, optionally scoped to a query.
 * Used by the trigger condition builder to populate value multi-selects.
 */
@RestController
@RequestMapping("/api/v1/facets")
public class FacetValuesController {

    private final FacetValuesService facetValuesService;

    public FacetValuesController(FacetValuesService facetValuesService) {
        this.facetValuesService = facetValuesService;
    }

    @GetMapping("/values")
    public ResponseEntity<List<Map<String, Object>>> getFacetValues(
            @RequestParam String field,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "30") int size) {

        if (field == null || field.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        List<Map<String, Object>> values = facetValuesService.getFacetValues(field, query, size);
        return ResponseEntity.ok(values);
    }
}
