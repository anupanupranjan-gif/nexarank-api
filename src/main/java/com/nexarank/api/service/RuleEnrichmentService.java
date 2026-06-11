// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.nexarank.api.adapter.SearchEngineAdapterFactory;
import com.nexarank.api.model.EnrichedQuery;
import com.nexarank.api.model.MerchRule;
import com.nexarank.api.model.SearchEngineConfig;
import com.nexarank.api.port.SearchEnginePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RuleEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(RuleEnrichmentService.class);

    private final MerchRuleService ruleService;
    private final SearchEngineConfigService configService;
    private final SearchEngineAdapterFactory adapterFactory;
    private final RuleAbTestService abTestService;

    public RuleEnrichmentService(MerchRuleService ruleService,
                                  SearchEngineConfigService configService,
                                  SearchEngineAdapterFactory adapterFactory,
                                  RuleAbTestService abTestService) {
        this.ruleService    = ruleService;
        this.configService  = configService;
        this.adapterFactory = adapterFactory;
        this.abTestService  = abTestService;
    }

    /**
     * Core enrichment flow:
     * 1. Look up approved rules matching the query
     * 2. Get search engine config (from DB)
     * 3. Pick correct adapter (ES or Solr)
     * 4. Translate rules to engine-specific DSL
     * 5. Return EnrichedQuery
     *
     * @param query      the search query
     * @param engineType optional override (if null, uses stored config)
     * @param zone       search zone (search-results, category, etc.)
     */
    public EnrichedQuery enrich(String query, String engineType, String zone) {
        return enrich(query, engineType, zone, null, null);
    }

    public EnrichedQuery enrich(String query, String engineType, String zone, String sessionId) {
        return enrich(query, engineType, zone, sessionId, null);
    }

    public EnrichedQuery enrich(String query, String engineType, String zone,
                                 String sessionId, java.util.Map<String, String> selectedFacets) {
        long start = System.currentTimeMillis();

        // A/B test resolution — only applies to QUERY_ONLY rules
        String abTestId  = null;
        String abVariant = null;
        List<MerchRule> abRules = List.of();
        var abContext = abTestService.resolveVariant(query, sessionId);
        if (abContext.isPresent()) {
            abTestId  = abContext.get().testId();
            abVariant = abContext.get().variant();
            abTestService.recordImpression(abTestId, abVariant);
            abRules = ruleService.getById(abContext.get().ruleId())
                    .map(List::of).orElse(List.of());
        }

        // Rule lookup — getRulesByQueryAndFacets handles both facet and non-facet cases
        List<MerchRule> allMatchingRules = ruleService.getRulesByQueryAndFacets(
                query, selectedFacets != null ? selectedFacets : java.util.Map.of());

        // Merge A/B variant rules with other matching rules (deduplicated by id)
        List<MerchRule> rules;
        if (!abRules.isEmpty()) {
            java.util.Map<String, MerchRule> merged = new java.util.LinkedHashMap<>();
            abRules.forEach(r -> merged.put(r.getId(), r));
            allMatchingRules.forEach(r -> merged.put(r.getId(), r));
            rules = new java.util.ArrayList<>(merged.values());
        } else {
            rules = allMatchingRules;
        }

        if (rules.isEmpty()) {
            log.debug("No rules found for query=\'{}\' facets={}, returning passthrough",
                    query, selectedFacets);
            return passthroughResult(query, engineType, start);
        }

        EnrichedQuery result = buildResult(query, engineType, zone, rules, start);
        if (abTestId != null) {
            result.setAbTestId(abTestId);
            result.setAbVariant(abVariant);
        }
        return result;
    }

    private EnrichedQuery buildResult(String query, String engineType, String zone,
                                       List<MerchRule> rules, long start) {
        log.debug("Found {} rules for query=\'{\'}", rules.size(), query);
        SearchEngineConfig config = configService.getConfig().orElse(null);
        if (config == null) {
            log.warn("No search engine config found, returning agnostic enrichment");
            return agnosticEnrichment(query, rules, engineType, start);
        }
        if (engineType != null) {
            try {
                config.setEngineType(SearchEngineConfig.EngineType.valueOf(engineType.toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("Unknown engine type \'{}\', using stored config type", engineType);
            }
        }
        try {
            SearchEnginePort adapter = adapterFactory.getAdapter(config);
            EnrichedQuery result = adapter.translateRules(query, rules, config);
            result.setProcessingMs(System.currentTimeMillis() - start);
            log.info("Enriched query=\'{}\' engine={} rules={} ms={}",
                query, config.getEngineType(),
                result.getAppliedRules().size(), result.getProcessingMs());
            return result;
        } catch (Exception e) {
            log.error("Rule translation failed for query=\'{}\': {}", query, e.getMessage(), e);
            return passthroughResult(query, engineType, start);
        }
    }

        private EnrichedQuery passthroughResult(String query, String engineType, long start) {
        EnrichedQuery result = new EnrichedQuery();
        result.setOriginalQuery(query);
        result.setExpandedQuery(query);
        result.setEngineType(engineType != null ? engineType : "UNKNOWN");
        result.setBoosts(List.of());
        result.setPins(List.of());
        result.setBuries(List.of());
        result.setAppliedRules(List.of());
        result.setProcessingMs(System.currentTimeMillis() - start);
        return result;
    }

    private EnrichedQuery agnosticEnrichment(String query, List<MerchRule> rules,
                                              String engineType, long start) {
        // Build engine-agnostic result without engine-specific DSL
        EnrichedQuery result = new EnrichedQuery();
        result.setOriginalQuery(query);
        result.setExpandedQuery(query);
        result.setEngineType(engineType != null ? engineType : "AGNOSTIC");

        List<EnrichedQuery.BoostInstruction> boosts = new java.util.ArrayList<>();
        List<EnrichedQuery.PinInstruction> pins = new java.util.ArrayList<>();
        List<EnrichedQuery.BuryInstruction> buries = new java.util.ArrayList<>();
        List<String> applied = new java.util.ArrayList<>();

        for (MerchRule rule : rules) {
            switch (rule.getType()) {
                case BOOST -> {
                    if (rule.getBoostField() != null) {
                        boosts.add(new EnrichedQuery.BoostInstruction(
                            rule.getBoostField(), rule.getBoostValue(),
                            rule.getBoostFactor() != null ? rule.getBoostFactor() : 1.5f));
                        applied.add(rule.getId());
                    }
                }
                case BURY -> {
                    if (rule.getBoostField() != null) {
                        buries.add(new EnrichedQuery.BuryInstruction(
                            rule.getBoostField(), rule.getBoostValue(),
                            rule.getBoostFactor() != null ? rule.getBoostFactor() : 0.1f));
                        applied.add(rule.getId());
                    }
                }
                case PIN -> {
                    if (rule.getPinnedIds() != null) {
                        for (int i = 0; i < rule.getPinnedIds().size(); i++) {
                            pins.add(new EnrichedQuery.PinInstruction(
                                rule.getPinnedIds().get(i), i + 1));
                        }
                        applied.add(rule.getId());
                    }
                }
                case SYNONYM -> {
                    if (rule.getSynonyms() != null) {
                        result.setExpandedQuery(query + " " +
                            String.join(" ", rule.getSynonyms()));
                        applied.add(rule.getId());
                    }
                }
            }
        }

        result.setBoosts(boosts);
        result.setPins(pins);
        result.setBuries(buries);
        result.setAppliedRules(applied);
        result.setProcessingMs(System.currentTimeMillis() - start);
        return result;
    }
}
