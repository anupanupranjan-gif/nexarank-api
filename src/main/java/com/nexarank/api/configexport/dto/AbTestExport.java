// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.configexport.dto;

/**
 * Only RUNNING tests are exported (see ConfigExportService) — impressions/
 * clicks/winnerId are live traffic stats tied to the source environment, not
 * "configuration" to replicate, so they're deliberately left out here.
 * ruleAId/ruleBId are cross-references into RuleExport.id within the same
 * bundle — NR-167 must validate both referenced rules are present in
 * rules.json before importing an A/B test.
 */
public record AbTestExport(String id, String query, String ruleAId, String ruleBId) {}
