// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.configexport.dto;

/** No id — stage_name is the natural key (one row per tenant/project/stage_name). */
public record PipelineStageExport(String stageName, String stageGroup, int stageOrder, boolean enabled) {}
