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

    public RuleEnrichmentService(MerchRuleService ruleService,
                                  SearchEngineConfigService configService,
                                  SearchEngineAdapterFactory adapterFactory) {
        this.ruleService = ruleService;
        this.configService = configService;
        this.adapterFactory = adapterFactory;
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
        long start = System.currentTimeMillis();

        // Step 1: Get matching approved rules
        List<MerchRule> rules = ruleService.getRulesByQuery(query);

        if (rules.isEmpty()) {
            log.debug("No rules found for query='{}', returning passthrough", query);
            return passthroughResult(query, engineType, start);
        }

        log.debug("Found {} rules for query='{}'", rules.size(), query);

        // Step 2: Get search engine config
        SearchEngineConfig config = configService.getConfig().orElse(null);

        if (config == null) {
            // No config stored — return engine-agnostic result with rules applied
            log.warn("No search engine config found, returning agnostic enrichment");
            return agnosticEnrichment(query, rules, engineType, start);
        }

        // Override engine type if caller specified one
        if (engineType != null) {
            try {
                config.setEngineType(SearchEngineConfig.EngineType.valueOf(engineType.toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("Unknown engine type '{}', using stored config type", engineType);
            }
        }

        // Step 3: Pick adapter and translate
        try {
            SearchEnginePort adapter = adapterFactory.getAdapter(config);
            EnrichedQuery result = adapter.translateRules(query, rules, config);
            result.setProcessingMs(System.currentTimeMillis() - start);

            log.info("Enriched query='{}' engine={} rules={} ms={}",
                query, config.getEngineType(),
                result.getAppliedRules().size(), result.getProcessingMs());

            return result;

        } catch (Exception e) {
            log.error("Rule translation failed for query='{}': {}", query, e.getMessage(), e);
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
