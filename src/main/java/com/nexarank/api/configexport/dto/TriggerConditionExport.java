// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.configexport.dto;

import java.util.List;

/** Shared by RuleExport and ContentRuleExport — same shape as RuleTriggerCondition. */
public record TriggerConditionExport(String facetField, List<String> facetValues) {}
