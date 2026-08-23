// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.compliance.PiiClassificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * NR-155: which entity fields carry regulated/sensitive data, and why —
 * the data map a GDPR Article 30 record or a HIPAA scoping exercise starts
 * from. ADMIN only, via the existing /api/v1/admin/** matcher in
 * SecurityConfig — no dedicated matcher needed.
 */
@RestController
@RequestMapping("/api/v1/admin/data-classification")
public class DataClassificationController {

    private final PiiClassificationService classificationService;

    public DataClassificationController(PiiClassificationService classificationService) {
        this.classificationService = classificationService;
    }

    @GetMapping
    public Object list() {
        return classificationService.classify();
    }
}
