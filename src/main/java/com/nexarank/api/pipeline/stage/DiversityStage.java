// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.pipeline.stage;

import com.nexarank.api.model.EnrichedQuery;
import com.nexarank.api.pipeline.PipelineContext;
import com.nexarank.api.pipeline.PipelineStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * POST_QUERY stage: adds diversity hints to EnrichedQuery.
 *
 * Tells the client how many results per brand and per category to show
 * before introducing variety. The client enforces these limits when
 * rendering or post-processing their search results.
 *
 * Defaults are conservative — tunable per project via pipeline_stage_config
 * metadata (future 26e config panel work).
 *
 * Why this matters for B2B search (FleetPride demo story):
 * A search for "oil filter" returning 10 results all from the same brand
 * gives the buyer no choice. Diversity hints ensure the client surfaces
 * options across brands and categories.
 */
@Component
public class DiversityStage implements PipelineStage {

    private static final Logger log = LoggerFactory.getLogger(DiversityStage.class);

    // Default diversity limits — reasonable for most eCommerce contexts
    private static final int DEFAULT_MAX_PER_BRAND    = 3;
    private static final int DEFAULT_MAX_PER_CATEGORY = 5;

    @Override public String name()       { return "DIVERSITY"; }
    @Override public StageGroup group()  { return StageGroup.POST_QUERY; }
    @Override public int defaultOrder()  { return 20; }

    @Override
    public void execute(PipelineContext context) {
        long start = System.currentTimeMillis();

        if (context.isMatchAll()) {
            context.addTrace(name(), "", "skipped (match-all)", 0, true);
            return;
        }

        try {
            // NAVIGATIONAL queries (part numbers, SKUs) don't need diversity —
            // user wants a specific product, not variety
            if ("NAVIGATIONAL".equals(context.getQueryClass())) {
                context.addTrace(name(), context.getCurrentQuery(),
                    "skipped (navigational)", 0, true);
                return;
            }

            EnrichedQuery enriched = context.getEnrichedQuery();
            if (enriched == null) {
                context.addTrace(name(), "", "skipped (no enriched query)", 0, true);
                return;
            }

            enriched.setMaxPerBrand(DEFAULT_MAX_PER_BRAND);
            enriched.setMaxPerCategory(DEFAULT_MAX_PER_CATEGORY);

            long took = System.currentTimeMillis() - start;
            log.debug("DIVERSITY maxPerBrand={} maxPerCategory={} took={}ms",
                DEFAULT_MAX_PER_BRAND, DEFAULT_MAX_PER_CATEGORY, took);
            context.addTrace(name(), context.getCurrentQuery(),
                String.format("maxPerBrand=%d maxPerCategory=%d",
                    DEFAULT_MAX_PER_BRAND, DEFAULT_MAX_PER_CATEGORY),
                took, false);

        } catch (Exception e) {
            long took = System.currentTimeMillis() - start;
            log.warn("DIVERSITY failed: {} — skipping", e.getMessage());
            context.addTrace(name(), "", "error: " + e.getMessage(), took, false);
        }
    }
}
