// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.model.SearchQualityResult;
import com.nexarank.api.security.TenantContext;
import com.nexarank.api.service.SearchQualityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/search-quality")
public class SearchQualityController {

    private final SearchQualityService service;

    public SearchQualityController(SearchQualityService service) {
        this.service = service;
    }

    /**
     * GET latest cached result — fast, no API calls. NR-121: scoped to the
     * caller's own tenant+project, not a global shared result.
     */
    @GetMapping
    public ResponseEntity<SearchQualityResult> getLatest() {
        SearchQualityResult result = service.getLastResult(TenantContext.getTenantId(), TenantContext.getProjectId());
        if (result == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(result);
    }

    /**
     * POST to trigger a fresh evaluation run.
     * Calls search-api for each query, computes NDCG/MRR.
     * Takes 30-60 seconds depending on search-api response time.
     */
    @PostMapping("/run")
    public ResponseEntity<SearchQualityResult> runEvaluation() {
        SearchQualityResult result = service.runEvaluation(TenantContext.getTenantId(), TenantContext.getProjectId());
        return ResponseEntity.ok(result);
    }
}
