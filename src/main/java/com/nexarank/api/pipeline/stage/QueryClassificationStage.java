// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.pipeline.stage;

import com.nexarank.api.pipeline.PipelineContext;
import com.nexarank.api.pipeline.PipelineStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Classifies the query into an intent category.
 * Written to context.queryClass for use by downstream stages and the pipeline preview UI.
 *
 * Intent classes:
 *   NAVIGATIONAL  — user knows exactly what they want (part number, SKU, brand+model)
 *   TRANSACTIONAL — user is ready to buy (contains commercial intent signals)
 *   INFORMATIONAL — broad research query
 *   CATEGORICAL   — browsing a category rather than a specific product
 *
 * This is a signal, not a hard gate. Downstream stages can use it to adjust behavior —
 * e.g. PIN rules are most valuable for NAVIGATIONAL queries, personalization matters
 * most for INFORMATIONAL queries.
 *
 * Currently rule-based. In a later phase this can be replaced by the LLM classifier
 * without changing any other stage — just swap this @Component.
 */
@Component
public class QueryClassificationStage implements PipelineStage {

    private static final Logger log = LoggerFactory.getLogger(QueryClassificationStage.class);

    // Part number / SKU patterns — strong navigational signal
    private static final java.util.regex.Pattern PART_NUMBER_PATTERN =
        java.util.regex.Pattern.compile(".*[A-Z]{1,4}[-]?\\d{3,}.*", java.util.regex.Pattern.CASE_INSENSITIVE);

    // Commercial intent keywords
    private static final Set<String> TRANSACTIONAL_SIGNALS = Set.of(
        "buy", "order", "cheap", "deal", "discount", "price", "sale",
        "best", "top", "review", "vs", "compare", "kit", "set", "pack"
    );

    // Category browsing keywords
    private static final Set<String> CATEGORICAL_SIGNALS = Set.of(
        "batteries", "filters", "brakes", "tires", "tyres", "lights",
        "parts", "accessories", "tools", "fluids", "oils", "belts",
        "hoses", "wipers", "exhausts", "transmissions", "engines"
    );

    @Override public String name()       { return "QUERY_CLASSIFICATION"; }
    @Override public StageGroup group()  { return StageGroup.PRE_QUERY; }
    @Override public int defaultOrder()  { return 30; }  // runs last in PRE_QUERY

    @Override
    public void execute(PipelineContext context) {
        long start = System.currentTimeMillis();
        String query = context.getCurrentQuery();

        if (context.isMatchAll()) {
            context.setQueryClass("CATEGORICAL");
            context.addTrace(name(), query, "CATEGORICAL (match-all)", 0, false);
            return;
        }

        String classification = classify(query);
        context.setQueryClass(classification);

        long took = System.currentTimeMillis() - start;
        log.debug("QUERY_CLASSIFICATION '{}' -> {} took={}ms", query, classification, took);
        context.addTrace(name(), query, classification, took, false);
    }

    private String classify(String query) {
        String lower = query.toLowerCase().trim();
        String[] tokens = lower.split("\\s+");

        // Part number or SKU — strong navigational signal
        if (PART_NUMBER_PATTERN.matcher(query).matches()) {
            return "NAVIGATIONAL";
        }

        // Very short single-token queries pointing at a category
        if (tokens.length == 1 && CATEGORICAL_SIGNALS.contains(lower)) {
            return "CATEGORICAL";
        }

        // Check for transactional signals
        for (String token : tokens) {
            if (TRANSACTIONAL_SIGNALS.contains(token)) return "TRANSACTIONAL";
        }

        // Check for categorical signals in multi-token queries
        for (String token : tokens) {
            if (CATEGORICAL_SIGNALS.contains(token)) return "CATEGORICAL";
        }

        // Default: informational
        return "INFORMATIONAL";
    }
}
