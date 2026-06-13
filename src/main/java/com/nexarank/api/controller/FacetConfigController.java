// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.model.FacetConfig;
import com.nexarank.api.service.FacetConfigService;
import com.nexarank.api.service.FacetVisibilityService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/facets")
public class FacetConfigController {

    private final FacetConfigService service;
    private final FacetVisibilityService facetVisibilityService;

    public FacetConfigController(FacetConfigService service,
                                   FacetVisibilityService facetVisibilityService) {
        this.service = service;
        this.facetVisibilityService = facetVisibilityService;
    }

    @GetMapping
    public List<FacetConfig> getFacets(
            @RequestParam(defaultValue = "false") boolean enabledOnly,
            @RequestParam java.util.Map<String, String> allParams) {

        List<FacetConfig> facets = enabledOnly
                ? service.getEnabledFacets()
                : service.getAllFacets();

        // Extract facet_ prefixed params as selectedFacets context
        java.util.Map<String, String> selectedFacets = allParams.entrySet().stream()
                .filter(e -> e.getKey().startsWith("facet_"))
                .collect(Collectors.toMap(
                        e -> e.getKey().substring(6),
                        java.util.Map.Entry::getValue));

        if (!selectedFacets.isEmpty()) {
            facets = facetVisibilityService.applyVisibilityRules(
                    service.getAllFacets(), selectedFacets);
        }

        return facets;
    }

    @GetMapping("/{id}")
    public ResponseEntity<FacetConfig> getById(@PathVariable String id) {
        return service.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createFacet(@RequestBody FacetConfig facet) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.createFacet(facet));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<FacetConfig> updateFacet(@PathVariable String id,
                                                     @RequestBody FacetConfig facet) {
        return service.updateFacet(id, facet)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<FacetConfig> toggleFacet(@PathVariable String id) {
        return service.toggleFacet(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFacet(@PathVariable String id) {
        service.deleteFacet(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/seed")
    public ResponseEntity<Map<String, String>> seedDefaults() {
        service.seedDefaultFacets();
        return ResponseEntity.ok(Map.of("message", "Default facets seeded successfully"));
    }
}
