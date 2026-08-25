// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.configexport.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ContentRuleExport(
        String id,
        String zone,
        String name,
        String description,
        int priority,
        Instant scheduleStart,
        Instant scheduleEnd,
        List<TriggerConditionExport> triggerConditions,
        Map<String, String> contentPayload) {}
