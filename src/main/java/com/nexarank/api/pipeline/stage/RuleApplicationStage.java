// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.pipeline.stage;

import com.nexarank.api.adapter.SearchEngineAdapterFactory;
import com.nexarank.api.model.EnrichedQuery;
import com.nexarank.api.model.MerchRule;
import com.nexarank.api.model.SearchEngineConfig;
import com.nexarank.api.pipeline.PipelineContext;
import com.nexarank.api.pipeline.PipelineStage;
import com.nexarank.api.port.SearchEnginePort;
import com.nexarank.api.service.MerchRuleService;
import com.nexarank.api.service.RuleAbTestService;
import com.nexarank.api.service.SearchEngineConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wraps the existing rule lookup + A/B resolution + engine translation logic
 * that previously lived inline in RuleEnrichmentService.enrich().
 *
 * Behavior is identical to before — this is a pure refactor into a stage.
 * RuleEnrichmentService now delegates to the orchestrator instead of doing this inline.
 */
@Component
public class RuleApplicationStage implements PipelineStage {

    private static final Logger log = LoggerFactory.getLogger(RuleApplicationStage.class);

    private final MerchRuleService ruleService;
    private final SearchEngineConfigService configService;
    private final SearchEngineAdapterFactory adapterFactory;
    private final RuleAbTestService abTestService;

    public RuleApplicationStage(MerchRuleService ruleService,
                                 SearchEngineConfigService configService,
                                 SearchEngineAdapterFactory adapterFactory,
                                 RuleAbTestService abTestService) {
        this.ruleService   = ruleService;
        this.configService = configService;
        this.adapterFactory = adapterFactory;
        this.abTestService  = abTestService;
    }

    @Override public String name()        { return "RULE_APPLICATION"; }
    @Override public StageGroup group()   { return StageGroup.RULE_APPLICATION; }
    @Override public int defaultOrder()   { return 10; }

    @Override
    public void execute(PipelineContext context) {
        long start = System.currentTimeMillis();
        String query = context.getCurrentQuery();
        String engineType = context.getEngineType() != null
                ? context.getEngineType()
                : configService.getConfig()
                .map(c -> c.getEngineType().name())
                .orElse("UNKNOWN");

        try {
            // A/B test resolution
            String abTestId  = null;
            String abVariant = null;
            List<MerchRule> abRules = List.of();
            var abCtx = abTestService.resolveVariant(query, context.getSessionId());
            if (abCtx.isPresent()) {
                abTestId  = abCtx.get().testId();
                abVariant = abCtx.get().variant();
                abTestService.recordImpression(abTestId, abVariant);
                abRules = ruleService.getById(abCtx.get().ruleId())
                    .map(List::of).orElse(List.of());
                context.setAbTestId(abTestId);
                context.setAbVariant(abVariant);
            }

            // Rule lookup using the (possibly pre-query-rewritten) currentQuery
            List<MerchRule> allRules = ruleService.getRulesByQueryAndFacets(
                query, context.getSelectedFacets());

            // Merge A/B rules (A/B rules take precedence, deduplicated by id)
            List<MerchRule> rules;
            if (!abRules.isEmpty()) {
                Map<String, MerchRule> merged = new LinkedHashMap<>();
                abRules.forEach(r -> merged.put(r.getId(), r));
                allRules.forEach(r -> merged.put(r.getId(), r));
                rules = new ArrayList<>(merged.values());
            } else {
                rules = allRules;
            }

            context.setMatchedRules(rules);

            if (rules.isEmpty()) {
                log.debug("RULE_APPLICATION: no rules for query='{}', passthrough", query);
                context.setEnrichedQuery(passthroughResult(query, resolveEngineType(engineType), start));
                context.addTrace(name(), query, "no rules matched", 
                    System.currentTimeMillis() - start, false);
                return;
            }

            // Translate rules to engine DSL via existing adapter
            SearchEngineConfig config = configService.getConfig().orElse(null);
            EnrichedQuery result;

            if (config == null) {
                log.warn("RULE_APPLICATION: no engine config, agnostic enrichment");
                result = agnosticEnrichment(query, rules, engineType, start);
            } else {
                // Allow engineType override from request
                if (engineType != null) {
                    try {
                        config.setEngineType(SearchEngineConfig.EngineType
                            .valueOf(engineType.toUpperCase()));
                    } catch (IllegalArgumentException e) {
                        log.warn("Unknown engine type '{}', using stored config", engineType);
                    }
                }
                SearchEnginePort adapter = adapterFactory.getAdapter(config);
                result = adapter.translateRules(query, rules, config);
            }

            result.setProcessingMs(System.currentTimeMillis() - start);
            if (abTestId != null) {
                result.setAbTestId(abTestId);
                result.setAbVariant(abVariant);
            }

            context.setEnrichedQuery(result);

            long took = System.currentTimeMillis() - start;
            String output = String.format("rules=%d boosts=%d pins=%d buries=%d",
                result.getAppliedRules().size(),
                result.getBoosts()  != null ? result.getBoosts().size()  : 0,
                result.getPins()    != null ? result.getPins().size()    : 0,
                result.getBuries()  != null ? result.getBuries().size()  : 0);

            log.info("RULE_APPLICATION query='{}' {} took={}ms", query, output, took);
            context.addTrace(name(), "query=" + query, output, took, false);

        } catch (Exception e) {
            long took = System.currentTimeMillis() - start;
            log.error("RULE_APPLICATION failed for query='{}': {}", query, e.getMessage(), e);
            context.setEnrichedQuery(passthroughResult(query, engineType, start));
            context.addTrace(name(), query, "error: " + e.getMessage(), took, false);
        }
    }

    // ── Helpers (copied from RuleEnrichmentService — same logic) ─────────────

    private EnrichedQuery passthroughResult(String query, String engineType, long start) {
        EnrichedQuery r = new EnrichedQuery();
        r.setOriginalQuery(query);
        r.setExpandedQuery(query);
        r.setEngineType(engineType != null ? engineType : "UNKNOWN");
        r.setBoosts(List.of());
        r.setPins(List.of());
        r.setBuries(List.of());
        r.setAppliedRules(List.of());
        r.setProcessingMs(System.currentTimeMillis() - start);
        return r;
    }

    private EnrichedQuery agnosticEnrichment(String query, List<MerchRule> rules,
                                              String engineType, long start) {
        EnrichedQuery result = new EnrichedQuery();
        result.setOriginalQuery(query);
        result.setExpandedQuery(query);
        result.setEngineType(engineType != null ? engineType : "AGNOSTIC");

        List<EnrichedQuery.BoostInstruction> boosts = new ArrayList<>();
        List<EnrichedQuery.PinInstruction>   pins   = new ArrayList<>();
        List<EnrichedQuery.BuryInstruction>  buries = new ArrayList<>();
        List<String> applied = new ArrayList<>();

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
                        for (int i = 0; i < rule.getPinnedIds().size(); i++)
                            pins.add(new EnrichedQuery.PinInstruction(
                                rule.getPinnedIds().get(i), i + 1));
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
    private String resolveEngineType(String requestEngineType) {
        if (requestEngineType != null) return requestEngineType;
        return configService.getConfig()
                .map(c -> c.getEngineType().name())
                .orElse("UNKNOWN");
    }
}
