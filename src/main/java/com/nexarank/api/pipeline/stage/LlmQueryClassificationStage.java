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

import java.util.List;

/**
 * LLM-powered query intent classification (NR-56) — runs immediately after
 * the rule-based QueryClassificationStage (order=30) in the same PRE_QUERY
 * group, at order=31.
 *
 * This is NOT a mutually-exclusive replacement stage. QueryClassificationStage
 * always runs first and always sets context.queryClass via cheap regex/keyword
 * logic — this stage only overwrites that value when the LLM call succeeds and
 * returns one of the four known labels. That ordering gives "fallback to
 * rule-based classification if LLM unavailable" for free: if this stage is
 * disabled, has no LLM config, times out, or returns something unparseable,
 * context.queryClass simply keeps whatever the rule-based stage already set —
 * there is no separate fallback code path to maintain or get out of sync.
 *
 * Per-project toggle (ticket's explicit requirement): this stage's own
 * pipeline_stage_config row (seeded disabled by default — V44) is the toggle.
 * Enabling it opts a project into LLM classification; disabling it (or simply
 * never enabling it) keeps pure rule-based behavior, byte-identical to before
 * this stage existed. Toggled the same way as every other stage, via the
 * existing Pipeline Editor UI (NR-39) — no special-case UI/API needed.
 */
@Component
public class LlmQueryClassificationStage implements PipelineStage {

    private static final Logger log = LoggerFactory.getLogger(LlmQueryClassificationStage.class);

    private static final List<String> VALID_CLASSES =
            List.of("NAVIGATIONAL", "TRANSACTIONAL", "CATEGORICAL", "INFORMATIONAL");

    private static final String PROMPT_TEMPLATE =
            "Classify the eCommerce search intent of the query into exactly one label.\n" +
            "NAVIGATIONAL: user wants a specific product, brand+model, or part/SKU number.\n" +
            "TRANSACTIONAL: user is ready to buy or is comparing price/deals (buy, cheap, deal, best, vs).\n" +
            "CATEGORICAL: user is browsing a general product category, not a specific item.\n" +
            "INFORMATIONAL: broad research query, none of the above.\n" +
            "Respond with only the single label word, nothing else.\n\n" +
            "Query: %s\nLabel:";

    private final LlmConfigService llmConfigService;
    private final LlmAdapterFactory adapterFactory;

    public LlmQueryClassificationStage(LlmConfigService llmConfigService, LlmAdapterFactory adapterFactory) {
        this.llmConfigService = llmConfigService;
        this.adapterFactory   = adapterFactory;
    }

    @Override public String name()       { return "LLM_QUERY_CLASSIFICATION"; }
    @Override public StageGroup group()  { return StageGroup.PRE_QUERY; }
    @Override public int defaultOrder()  { return 31; }

    @Override
    public void execute(PipelineContext context) {
        long start = System.currentTimeMillis();
        String query = context.getCurrentQuery();

        if (context.isMatchAll()) {
            context.addTrace(name(), query, "skipped (match-all)", 0, true);
            return;
        }

        LlmConfig config = llmConfigService.getConfig().orElse(null);
        if (config == null) {
            context.addTrace(name(), query, "skipped (no LLM config) — rule-based classification stands", 0, true);
            return;
        }

        try {
            LlmPort adapter = adapterFactory.getAdapter(config);
            String raw = adapter.classify(query, PROMPT_TEMPLATE, config);
            long took = System.currentTimeMillis() - start;

            String matched = raw == null ? null : VALID_CLASSES.stream()
                    .filter(raw::contains)
                    .findFirst()
                    .orElse(null);

            if (matched == null) {
                String ruleBased = context.getQueryClass();
                log.warn("LLM_QUERY_CLASSIFICATION unparseable response '{}' for query='{}' — keeping rule-based '{}'",
                        raw, query, ruleBased);
                context.addTrace(name(), query, "unparseable ('" + raw + "'), kept rule-based: " + ruleBased, took, false);
                return;
            }

            String previous = context.getQueryClass();
            context.setQueryClass(matched);
            log.info("LLM_QUERY_CLASSIFICATION '{}' -> {} (rule-based was {}) took={}ms provider={}",
                    query, matched, previous, took, config.getProvider());
            context.addTrace(name(), query, matched + " (rule-based was " + previous + ")", took, false);

        } catch (Exception e) {
            long took = System.currentTimeMillis() - start;
            String ruleBased = context.getQueryClass();
            log.warn("LLM_QUERY_CLASSIFICATION failed for '{}': {} — keeping rule-based '{}'",
                    query, e.getMessage(), ruleBased);
            context.addTrace(name(), query, "error: " + e.getMessage() + ", kept rule-based: " + ruleBased, took, false);
        }
    }
}
