// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.configexport.dto;

public record JudgmentExport(
        String id,
        String query,
        String productId,
        String productTitle,
        int grade,
        String source,
        String status,
        Integer llmGrade) {}
