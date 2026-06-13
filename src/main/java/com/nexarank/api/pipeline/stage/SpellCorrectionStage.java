// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.pipeline.stage;

import com.nexarank.api.pipeline.PipelineContext;
import com.nexarank.api.pipeline.PipelineStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Corrects common eCommerce search misspellings before rule matching.
 *
 * This is intentionally a lightweight dictionary-based approach, not a full
 * spell-check engine. The goal is to catch high-frequency typos that would
 * otherwise miss rule matches (e.g. "battrey" misses the "battery" BOOST rule).
 *
 * The corrections map is the place to grow this over time — add entries based
 * on zero-result query analysis from the Analytics Dashboard.
 *
 * For a production deployment, this would be backed by a DB table so corrections
 * can be managed without a redeploy. That's a future enhancement — for now the
 * dictionary lives here and is easy to extend.
 *
 * Does NOT run on match-all queries (*).
 */
@Component
public class SpellCorrectionStage implements PipelineStage {

    private static final Logger log = LoggerFactory.getLogger(SpellCorrectionStage.class);

    // Common eCommerce misspellings → correct form
    // Grow this from zero-result query data in the Analytics Dashboard
    private static final Map<String, String> CORRECTIONS = Map.ofEntries(
        // Automotive
        Map.entry("battrey",    "battery"),
        Map.entry("baterry",    "battery"),
        Map.entry("bateery",    "battery"),
        Map.entry("alternatr",  "alternator"),
        Map.entry("alternater", "alternator"),
        Map.entry("braek",      "brake"),
        Map.entry("breake",     "brake"),
        Map.entry("exhuast",    "exhaust"),
        Map.entry("transmision","transmission"),
        // General
        Map.entry("fliters",    "filters"),
        Map.entry("filtr",      "filter"),
        Map.entry("wipers",     "wipers"),
        Map.entry("wiper",      "wiper"),
        Map.entry("headligt",   "headlight"),
        Map.entry("headlght",   "headlight"),
        Map.entry("tyre",       "tire"),   // UK/US normalization
        Map.entry("tyres",      "tires"),
        // Heavy duty / FleetPride relevant
        Map.entry("truk",       "truck"),
        Map.entry("trucs",      "trucks"),
        Map.entry("hoses",      "hoses"),
        Map.entry("brakepads",  "brake pads"),
        Map.entry("airfilter",  "air filter"),
        Map.entry("oilfilter",  "oil filter")
    );

    @Override public String name()       { return "SPELL_CORRECTION"; }
    @Override public StageGroup group()  { return StageGroup.PRE_QUERY; }
    @Override public int defaultOrder()  { return 20; }  // runs after STOPWORD_REMOVAL

    @Override
    public void execute(PipelineContext context) {
        long start = System.currentTimeMillis();
        String input = context.getCurrentQuery();

        if (context.isMatchAll()) {
            context.addTrace(name(), input, "skipped (match-all)", 0, true);
            return;
        }

        String[] tokens = input.trim().toLowerCase().split("\\s+");
        boolean corrected = false;

        String[] fixed = new String[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            String correction = CORRECTIONS.get(tokens[i]);
            if (correction != null) {
                fixed[i] = correction;
                corrected = true;
                log.debug("SPELL_CORRECTION '{}' -> '{}'", tokens[i], correction);
            } else {
                fixed[i] = tokens[i];
            }
        }

        long took = System.currentTimeMillis() - start;

        if (!corrected) {
            context.addTrace(name(), input, "no corrections", took, true);
            return;
        }

        String result = Arrays.stream(fixed).collect(Collectors.joining(" "));
        context.setCurrentQuery(result);
        log.info("SPELL_CORRECTION '{}' -> '{}' took={}ms", input, result, took);
        context.addTrace(name(), input, result, took, false);
    }
}
