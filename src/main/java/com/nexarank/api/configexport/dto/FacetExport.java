// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.configexport.dto;

public record FacetExport(
        String id,
        String fieldName,
        String displayLabel,
        String facetType,
        boolean enabled,
        boolean showCount,
        int sortOrder,
        Integer maxValues,
        Double rangeMin,
        Double rangeMax,
        Double rangeInterval) {}
