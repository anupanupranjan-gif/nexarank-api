// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.pipeline.stage;

import com.nexarank.api.pipeline.PipelineContext;
import com.nexarank.api.pipeline.PipelineStage;
import com.nexarank.api.service.LiveQuerySynonymCandidateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * NR-57 — widens synonym suggestions from zero-result-only (the existing,
 * on-demand AiRuleSuggestionService.suggestSynonymsForZeroResults(), built
 * for NR-69) to every live query.
 *
 * Deliberately does NOT call the LLM inline. This pipeline runs synchronously
 * on the hot search path (same as every other stage here), and gemma3:1b
 * alone takes ~0.5-2s per call on this CPU-only dev VM (NR-97) — paying that
 * on every live search, for a suggestion nobody may ever look at, would be
 * exactly the kind of latency regression NR-97 fixed for query rewrite.
 * Instead this stage just upserts a cheap frequency counter
 * (LiveQuerySynonymCandidateService, single indexed write). Real LLM synonym
 * generation happens later, on-demand, only for the top few queries a
 * merchandiser actually opens the AI Suggestions page to review — same
 * "surfaced for review, not auto-applied" principle the ticket asks for,
 * just applied to when the LLM call itself happens too.
 *
 * Toggle-gated like every other LLM-adjacent stage (pipeline_stage_config,
 * seeded disabled — V45): a customer who doesn't want the extra per-query
 * DB write can leave it off with zero behavior change.
 */
@Component
public class LlmSynonymSuggestionStage implements PipelineStage {

    private static final Logger log = LoggerFactory.getLogger(LlmSynonymSuggestionStage.class);

    private final LiveQuerySynonymCandidateService candidateService;

    public LlmSynonymSuggestionStage(LiveQuerySynonymCandidateService candidateService) {
        this.candidateService = candidateService;
    }

    @Override public String name()       { return "LLM_SYNONYM_SUGGESTION"; }
    @Override public StageGroup group()  { return StageGroup.PRE_QUERY; }
    @Override public int defaultOrder()  { return 35; }

    @Override
    public void execute(PipelineContext context) {
        long start = System.currentTimeMillis();
        String query = context.getPreRewriteQuery();

        if (context.isMatchAll() || query == null || query.isBlank()) {
            context.addTrace(name(), query, "skipped (match-all/blank)", 0, true);
            return;
        }

        try {
            candidateService.track(context.getTenantId(), context.getProjectId(), query);
            context.addTrace(name(), query, "tracked for synonym-suggestion review",
                    System.currentTimeMillis() - start, false);
        } catch (Exception e) {
            log.warn("LLM_SYNONYM_SUGGESTION failed to track query='{}': {}", query, e.getMessage());
            context.addTrace(name(), query, "error: " + e.getMessage(),
                    System.currentTimeMillis() - start, false);
        }
    }
}
