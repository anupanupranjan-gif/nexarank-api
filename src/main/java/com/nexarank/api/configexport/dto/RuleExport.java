// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.configexport.dto;

import java.time.Instant;
import java.util.List;

/**
 * LIVE-state-only export of a MerchRule (NR-157 resolved decision #1 — not
 * version history). `id` is preserved from the source environment on
 * purpose: NR-167's import matches on it to decide new-row-vs-new-version
 * on a repeat import of the same bundle, rather than needing a separate
 * tracking column.
 */
public record RuleExport(
        String id,
        String type,
        String query,
        String boostField,
        String boostValue,
        Float boostFactor,
        List<String> pinnedIds,
        List<String> synonyms,
        String synonymDirection,
        int priority,
        boolean requireQuery,
        String redirectUrl,
        Instant activateAt,
        Instant expireAt,
        List<TriggerConditionExport> triggerConditions) {}
