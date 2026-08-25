// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.configexport.dto;

import java.util.List;
import java.util.Map;

/** NR-167: per-category counts + any non-fatal warnings, returned to the caller after an import. */
public record ImportSummary(
        Map<String, Integer> imported,
        Map<String, Integer> idCollisionsResolved,
        List<String> warnings) {}
