// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.configexport.dto;

import java.util.List;

/** suggestionConfig is null if the tenant/project never saved one (defaults apply). */
public record QualityCurationExport(
        ExportMeta meta,
        List<JudgmentSetExport> judgmentSets,
        SuggestionConfigExport suggestionConfig) {}
