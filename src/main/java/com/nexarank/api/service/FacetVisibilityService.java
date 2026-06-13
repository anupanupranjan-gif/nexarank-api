// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarank.api.model.FacetConfig;
import com.nexarank.api.model.FacetVisibilityRule;
import com.nexarank.api.repository.FacetVisibilityRuleRepository;
import com.nexarank.api.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Phase 24 / NR-38: Conditional facet visibility.
 *
 * Given the current selectedFacets context, determines which facets
 * should be visible and which should be hidden.
 *
 * Resolution:
 * 1. Start with all enabled facets
 * 2. Find all matching visibility rules (ordered by priority desc)
 * 3. Apply show/hide lists — show adds facets, hide removes them
 * 4. Higher priority rules win on conflict
 */
@Service
public class FacetVisibilityService {

    private static final Logger log = LoggerFactory.getLogger(FacetVisibilityService.class);

    private final FacetVisibilityRuleRepository repository;
    private final ObjectMapper objectMapper;

    public FacetVisibilityService(FacetVisibilityRuleRepository repository,
                                   ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    public List<FacetVisibilityRule> getAllRules() {
        return repository.findByTenantIdAndProjectIdOrderByPriorityDesc(
                TenantContext.getTenantId(), TenantContext.getProjectId())
                .stream().peek(this::deserialize).collect(Collectors.toList());
    }

    @Transactional
    public FacetVisibilityRule createRule(FacetVisibilityRule rule) {
        rule.setId(UUID.randomUUID().toString());
        rule.setTenantId(TenantContext.getTenantId());
        rule.setProjectId(TenantContext.getProjectId());
        rule.setCreatedBy(getCurrentUsername());
        rule.setCreatedAt(Instant.now());
        rule.setUpdatedAt(Instant.now());
        serialize(rule);
        FacetVisibilityRule saved = repository.save(rule);
        deserialize(saved);
        log.info("FACET_VISIBILITY_RULE_CREATED id={} trigger={}={} by={}",
                saved.getId(), saved.getTriggerFacetField(),
                saved.getTriggerFacetValue(), saved.getCreatedBy());
        return saved;
    }

    @Transactional
    public Optional<FacetVisibilityRule> updateRule(String id, FacetVisibilityRule updated) {
        return repository.findById(id)
                .filter(r -> r.getTenantId().equals(TenantContext.getTenantId()))
                .map(existing -> {
                    updated.setId(existing.getId());
                    updated.setTenantId(existing.getTenantId());
                    updated.setProjectId(existing.getProjectId());
                    updated.setCreatedBy(existing.getCreatedBy());
                    updated.setCreatedAt(existing.getCreatedAt());
                    updated.setUpdatedAt(Instant.now());
                    serialize(updated);
                    FacetVisibilityRule saved = repository.save(updated);
                    deserialize(saved);
                    return saved;
                });
    }

    public void deleteRule(String id) {
        repository.findById(id)
                .filter(r -> r.getTenantId().equals(TenantContext.getTenantId()))
                .ifPresent(repository::delete);
    }

    // ── Visibility evaluation ─────────────────────────────────────────────────

    /**
     * Filter a list of facets based on visibility rules for the given context.
     *
     * @param allFacets      all enabled facets from facet_config
     * @param selectedFacets current user context e.g. {"category": "Battery"}
     * @return filtered and ordered list of facets to show
     */
    public List<FacetConfig> applyVisibilityRules(List<FacetConfig> allFacets,
                                                    Map<String, String> selectedFacets) {
        if (selectedFacets == null || selectedFacets.isEmpty()) {
            // No context — return only facets that are globally enabled
            return allFacets.stream()
                    .filter(FacetConfig::isEnabled)
                    .collect(Collectors.toList());
        }

        // Find matching visibility rules ordered by priority
        List<FacetVisibilityRule> matchingRules = repository
                .findByTenantIdAndProjectIdAndEnabledOrderByPriorityDesc(
                        TenantContext.getTenantId(), TenantContext.getProjectId(), true)
                .stream()
                .filter(r -> {
                    String selected = selectedFacets.get(r.getTriggerFacetField());
                    return r.getTriggerFacetValue().equalsIgnoreCase(selected);
                })
                .peek(this::deserialize)
                .collect(Collectors.toList());

        if (matchingRules.isEmpty()) {
            return allFacets.stream().filter(FacetConfig::isEnabled).collect(Collectors.toList());
        }

        // Build show/hide sets from rules (higher priority already first)
        Set<String> toShow = new LinkedHashSet<>();
        Set<String> toHide = new LinkedHashSet<>();

        for (FacetVisibilityRule rule : matchingRules) {
            if (rule.getShowFacets() != null) toShow.addAll(rule.getShowFacets());
            if (rule.getHideFacets() != null) toHide.addAll(rule.getHideFacets());
        }
        // Show takes precedence over hide for same field
        toHide.removeAll(toShow);

        // Build result: enabled facets + shown facets - hidden facets
        Map<String, FacetConfig> facetMap = allFacets.stream()
                .collect(Collectors.toMap(FacetConfig::getFieldName, f -> f,
                        (a, b) -> a, LinkedHashMap::new));

        List<FacetConfig> result = new ArrayList<>();
        for (FacetConfig facet : allFacets) {
            String field = facet.getFieldName();
            if (toHide.contains(field)) continue;
            if (facet.isEnabled() || toShow.contains(field)) {
                result.add(facet);
            }
        }

        log.debug("Visibility rules applied: context={} matching={} show={} hide={} result={}",
                selectedFacets, matchingRules.size(), toShow, toHide,
                result.stream().map(FacetConfig::getFieldName).collect(Collectors.toList()));

        return result;
    }

    /**
     * Preview: return the effective facet list for a given context without saving anything.
     * Used by the Facet Manager preview button.
     */
    public Map<String, Object> previewVisibility(List<FacetConfig> allFacets,
                                                  String triggerField, String triggerValue) {
        Map<String, String> context = Map.of(triggerField, triggerValue);
        List<FacetConfig> visible = applyVisibilityRules(allFacets, context);

        Set<String> visibleFields = visible.stream()
                .map(FacetConfig::getFieldName).collect(Collectors.toSet());
        Set<String> hiddenFields = allFacets.stream()
                .map(FacetConfig::getFieldName)
                .filter(f -> !visibleFields.contains(f))
                .collect(Collectors.toSet());

        return Map.of(
                "context", context,
                "visible", visible.stream().map(f -> Map.of(
                        "fieldName", f.getFieldName(),
                        "displayLabel", f.getDisplayLabel() != null ? f.getDisplayLabel() : f.getFieldName(),
                        "facetType", f.getFacetType()
                )).collect(Collectors.toList()),
                "hidden", hiddenFields
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void serialize(FacetVisibilityRule rule) {
        try {
            if (rule.getShowFacets() != null)
                rule.setShowFacetsJson(objectMapper.writeValueAsString(rule.getShowFacets()));
            if (rule.getHideFacets() != null)
                rule.setHideFacetsJson(objectMapper.writeValueAsString(rule.getHideFacets()));
        } catch (Exception e) {
            log.warn("Failed to serialize visibility rule fields: {}", e.getMessage());
        }
    }

    private void deserialize(FacetVisibilityRule rule) {
        try {
            if (rule.getShowFacetsJson() != null)
                rule.setShowFacets(objectMapper.readValue(rule.getShowFacetsJson(),
                        new TypeReference<List<String>>() {}));
            if (rule.getHideFacetsJson() != null)
                rule.setHideFacets(objectMapper.readValue(rule.getHideFacetsJson(),
                        new TypeReference<List<String>>() {}));
        } catch (Exception e) {
            log.warn("Failed to deserialize visibility rule fields: {}", e.getMessage());
        }
    }

    private String getCurrentUsername() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
