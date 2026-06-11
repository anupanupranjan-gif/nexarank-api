// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarank.api.model.RuleTriggerCondition;
import com.nexarank.api.repository.RuleTriggerConditionRepository;
import com.nexarank.api.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 23 / NR-37 v2: multi-condition facet trigger logic.
 *
 * Condition matching:
 * - AND between conditions (all must match)
 * - OR between values within a condition (any value matches)
 *
 * If a rule has no conditions, it fires on query match alone (or always if requireQuery=false).
 */
@Service
public class RuleTriggerConditionService {

    private static final Logger log = LoggerFactory.getLogger(RuleTriggerConditionService.class);
    private final RuleTriggerConditionRepository repository;
    private final ObjectMapper objectMapper;

    public RuleTriggerConditionService(RuleTriggerConditionRepository repository,
                                        ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Transactional
    public List<RuleTriggerCondition> saveConditions(String ruleId,
                                                      List<Map<String, Object>> conditionDtos) {
        repository.deleteByRuleId(ruleId);
        if (conditionDtos == null || conditionDtos.isEmpty()) return List.of();

        String tenantId  = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();

        List<RuleTriggerCondition> saved = new java.util.ArrayList<>();
        for (int i = 0; i < conditionDtos.size(); i++) {
            Map<String, Object> dto = conditionDtos.get(i);
            String field = (String) dto.get("facetField");
            Object valuesRaw = dto.get("facetValues");
            if (field == null || valuesRaw == null) continue;

            @SuppressWarnings("unchecked")
            List<String> values = valuesRaw instanceof List
                    ? (List<String>) valuesRaw
                    : List.of(valuesRaw.toString());

            RuleTriggerCondition condition = new RuleTriggerCondition();
            condition.setId(UUID.randomUUID().toString());
            condition.setRuleId(ruleId);
            condition.setTenantId(tenantId);
            condition.setProjectId(projectId);
            condition.setFacetField(field);
            condition.setFacetValuesJson(serialize(values));
            condition.setPosition(i);
            saved.add(repository.save(condition));
        }
        return saved;
    }

    public List<RuleTriggerCondition> getConditions(String ruleId) {
        List<RuleTriggerCondition> conditions =
                repository.findByRuleIdOrderByPosition(ruleId);
        conditions.forEach(c -> c.setFacetValues(deserialize(c.getFacetValuesJson())));
        return conditions;
    }

    // ── Matching ──────────────────────────────────────────────────────────────

    /**
     * Returns true when all conditions match the selectedFacets (AND logic).
     * Each condition matches if any of its values matches the selected value (OR logic).
     *
     * If the rule has no conditions, returns true (fires on query/requireQuery logic alone).
     */
    public boolean conditionsMatch(String ruleId, Map<String, String> selectedFacets) {
        List<RuleTriggerCondition> conditions = getConditions(ruleId);
        if (conditions.isEmpty()) return true;
        if (selectedFacets == null || selectedFacets.isEmpty()) return false;

        for (RuleTriggerCondition condition : conditions) {
            String selectedValue = selectedFacets.get(condition.getFacetField());
            if (selectedValue == null) return false; // AND — all must match

            List<String> allowedValues = condition.getFacetValues();
            boolean anyMatch = allowedValues != null && allowedValues.stream()
                    .anyMatch(v -> v.equalsIgnoreCase(selectedValue));
            if (!anyMatch) return false;
        }
        return true;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String serialize(List<String> values) {
        try { return objectMapper.writeValueAsString(values); }
        catch (Exception e) { return "[]"; }
    }

    private List<String> deserialize(String json) {
        try { return objectMapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception e) { return List.of(); }
    }
}
