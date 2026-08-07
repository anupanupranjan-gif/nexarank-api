// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.pipeline;

import com.nexarank.api.model.EnrichedQuery;
import com.nexarank.api.security.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Runs all pipeline stages in group order: PRE_QUERY → RULE_APPLICATION → POST_QUERY
 *
 * Called by RuleEnrichmentService.enrich() — replaces the inline logic that was there.
 * Returns an EnrichedQuery, same shape as before. Zero changes to callers.
 *
 * Stage ordering/enablement: defaultOrder() is the fallback, but per-project
 * overrides from pipeline_stage_config (V18) — enabled + stage_order, editable
 * via the Pipeline Editor UI — are now genuinely applied per request via
 * PipelineStageConfigService. Previously this was a documented TODO and the
 * DB table was write-only from the UI's perspective; this closes that gap.
 */
@Service
public class QueryPipelineOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(QueryPipelineOrchestrator.class);

    private final List<PipelineStage> preQueryStages;
    private final List<PipelineStage> ruleStages;
    private final List<PipelineStage> postQueryStages;
    private final PipelineStageConfigService stageConfigService;

    // Rules-only enrich latency SLI. Wraps the RULE_APPLICATION group (rule
    // lookup, conflict resolution, DSL translation) — deliberately NOT the LLM
    // stages (those live in PRE_QUERY), so the latency SLO stays stable
    // regardless of whether an LLM stage is enabled per tenant/project.
    private final Timer rulesTimer;

    // Spring injects all @Component PipelineStage beans automatically
    public QueryPipelineOrchestrator(List<PipelineStage> allStages, PipelineStageConfigService stageConfigService,
                                     MeterRegistry meterRegistry) {
        this.preQueryStages  = sorted(allStages, PipelineStage.StageGroup.PRE_QUERY);
        this.ruleStages      = sorted(allStages, PipelineStage.StageGroup.RULE_APPLICATION);
        this.postQueryStages = sorted(allStages, PipelineStage.StageGroup.POST_QUERY);
        this.stageConfigService = stageConfigService;
        this.rulesTimer = Timer.builder("nexarank.enrich.rules")
            .description("Rules-only enrich latency (RULE_APPLICATION group; excludes LLM stages)")
            .publishPercentileHistogram()
            .register(meterRegistry);
        log.info("Pipeline stages loaded — pre={} rule={} post={}",
            names(preQueryStages), names(ruleStages), names(postQueryStages));
    }

    /**
     * Run the full pipeline and return the enriched query.
     * This is a drop-in replacement for the inline logic in RuleEnrichmentService.
     */
    public EnrichedQuery execute(String query, String engineType, String zone,
                                  String sessionId,
                                  java.util.Map<String, String> selectedFacets) {
        long start = System.currentTimeMillis();

        PipelineContext ctx = new PipelineContext(
            TenantContext.getTenantId(),
            TenantContext.getProjectId(),
            sessionId,
            query,
            engineType,
            zone,
            selectedFacets
        );

        String tenantId  = ctx.getTenantId();
        String projectId = ctx.getProjectId();

        // PRE_QUERY — spell correction, synonyms, LLM rewrite, classification (26b, 26c)
        runGroup(stageConfigService.applyOverrides(preQueryStages, tenantId, projectId), ctx);

        // RULE_APPLICATION — rule lookup, A/B, conflict resolution, DSL translation
        rulesTimer.record(() ->
            runGroup(stageConfigService.applyOverrides(ruleStages, tenantId, projectId), ctx));

        // POST_QUERY — personalization, filtering, diversity (26d)
        runGroup(stageConfigService.applyOverrides(postQueryStages, tenantId, projectId), ctx);

        EnrichedQuery result = ctx.getEnrichedQuery();
        if (result == null) {
            // Safety net — should not happen if RuleApplicationStage is registered
            log.warn("Pipeline produced no EnrichedQuery for query='{}', returning passthrough", query);
            result = passthrough(query, engineType, System.currentTimeMillis() - start);
        }

        log.debug("Pipeline complete query='{}' stages={} took={}ms",
            query, ctx.getTraces().size(), System.currentTimeMillis() - start);

        return result;
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void runGroup(List<PipelineStage> stages, PipelineContext ctx) {
        for (PipelineStage stage : stages) {
            long t = System.currentTimeMillis();
            try {
                stage.execute(ctx);
            } catch (Exception e) {
                // Belt-and-suspenders — stages should catch internally
                long took = System.currentTimeMillis() - t;
                log.error("Stage {} threw unexpectedly: {}", stage.name(), e.getMessage(), e);
                ctx.addTrace(stage.name(), "error", e.getMessage(), took, false);
            }
        }
    }

    private List<PipelineStage> sorted(List<PipelineStage> all,
                                        PipelineStage.StageGroup group) {
        return all.stream()
            .filter(s -> s.group() == group)
            .sorted(Comparator.comparingInt(PipelineStage::defaultOrder))
            .toList();
    }

    private List<String> names(List<PipelineStage> stages) {
        return stages.stream().map(PipelineStage::name).toList();
    }

    private EnrichedQuery passthrough(String query, String engineType, long ms) {
        EnrichedQuery r = new EnrichedQuery();
        r.setOriginalQuery(query);
        r.setExpandedQuery(query);
        r.setEngineType(engineType != null ? engineType : "UNKNOWN");
        r.setBoosts(List.of());
        r.setPins(List.of());
        r.setBuries(List.of());
        r.setAppliedRules(List.of());
        r.setProcessingMs(ms);
        return r;
    }
}
