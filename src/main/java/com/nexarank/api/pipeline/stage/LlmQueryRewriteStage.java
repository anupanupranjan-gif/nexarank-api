// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.pipeline.stage;

import com.nexarank.api.adapter.LlmAdapterFactory;
import com.nexarank.api.model.LlmConfig;
import com.nexarank.api.pipeline.PipelineContext;
import com.nexarank.api.pipeline.PipelineStage;
import com.nexarank.api.port.LlmPort;
import com.nexarank.api.service.LlmConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Rewrites/expands the search query using the tenant's configured LLM provider.
 *
 * Runs last in PRE_QUERY (order=40) — after stopword removal, spell correction,
 * and query classification. Works on the cleaned query, not the raw original.
 *
 * Key design principles:
 * - Never blocks search: if LLM is slow, unavailable, or misconfigured, falls
 *   back to the current query silently. Timeout enforced via LlmConfig.timeoutSeconds.
 * - Skipped if no LLM config found for the tenant/project.
 * - Skipped for match-all queries (*).
 * - NAVIGATIONAL queries skipped — user knows exactly what they want,
 *   LLM rewrite would likely hurt precision.
 * - originalQuery in EnrichedQuery is always set to the true original
 *   (context.getOriginalQuery()), not the rewritten form. Fixed here.
 */
@Component
public class LlmQueryRewriteStage implements PipelineStage {

    private static final Logger log = LoggerFactory.getLogger(LlmQueryRewriteStage.class);

    private final LlmConfigService llmConfigService;
    private final LlmAdapterFactory adapterFactory;

    public LlmQueryRewriteStage(LlmConfigService llmConfigService,
                                 LlmAdapterFactory adapterFactory) {
        this.llmConfigService = llmConfigService;
        this.adapterFactory   = adapterFactory;
    }

    @Override public String name()       { return "LLM_QUERY_REWRITE"; }
    @Override public StageGroup group()  { return StageGroup.PRE_QUERY; }
    @Override public int defaultOrder()  { return 40; }

    @Override
    public void execute(PipelineContext context) {
        long start = System.currentTimeMillis();
        String input = context.getCurrentQuery();

        // Skip match-all
        if (context.isMatchAll()) {
            context.addTrace(name(), input, "skipped (match-all)", 0, true);
            return;
        }

        // Skip navigational queries — user knows what they want
        if ("NAVIGATIONAL".equals(context.getQueryClass())) {
            context.addTrace(name(), input, "skipped (navigational query)", 0, true);
            return;
        }

        // Skip if no LLM config for this tenant/project
        LlmConfig config = llmConfigService.getConfig().orElse(null);
        if (config == null) {
            context.addTrace(name(), input, "skipped (no LLM config)", 0, true);
            return;
        }

        try {
            LlmPort adapter = adapterFactory.getAdapter(config);
            String rewritten = adapter.rewrite(
                input,
                config.getEffectivePromptTemplate(),
                config
            );

            long took = System.currentTimeMillis() - start;

            // If LLM returned the same query, nothing to do
            if (rewritten.equals(input)) {
                context.addTrace(name(), input, "no change", took, true);
                return;
            }

            context.setCurrentQuery(rewritten);
            log.info("LLM_QUERY_REWRITE '{}' -> '{}' took={}ms provider={}",
                input, rewritten, took, config.getProvider());
            context.addTrace(name(), input, rewritten, took, false);

        } catch (Exception e) {
            long took = System.currentTimeMillis() - start;
            log.warn("LLM_QUERY_REWRITE failed for '{}': {} — passthrough", input, e.getMessage());
            context.addTrace(name(), input, "error: " + e.getMessage(), took, false);
            // currentQuery unchanged — search continues with pre-LLM query
        }
    }
}
