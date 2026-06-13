// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.pipeline.stage;

import com.nexarank.api.pipeline.PipelineContext;
import com.nexarank.api.pipeline.PipelineStage;
import com.nexarank.api.service.StopwordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Removes stopwords from the query before rule matching and engine execution.
 *
 * Stopwords are loaded from the stopword_list table via StopwordService,
 * scoped per tenant/project. Manageable from the NexaRank UI without redeployment.
 * Results are cached in StopwordService — cache is invalidated on any add/delete.
 *
 * Does NOT run on match-all queries (*).
 * Skipped if removing stopwords would produce an empty query.
 */
@Component
public class StopwordRemovalStage implements PipelineStage {

    private static final Logger log = LoggerFactory.getLogger(StopwordRemovalStage.class);

    private final StopwordService stopwordService;

    public StopwordRemovalStage(StopwordService stopwordService) {
        this.stopwordService = stopwordService;
    }

    @Override public String name()       { return "STOPWORD_REMOVAL"; }
    @Override public StageGroup group()  { return StageGroup.PRE_QUERY; }
    @Override public int defaultOrder()  { return 10; }

    @Override
    public void execute(PipelineContext context) {
        long start = System.currentTimeMillis();
        String input = context.getCurrentQuery();

        if (context.isMatchAll()) {
            context.addTrace(name(), input, "skipped (match-all)", 0, true);
            return;
        }

        Set<String> stopwords = stopwordService.getStopwords();

        if (stopwords.isEmpty()) {
            context.addTrace(name(), input, "skipped (no stopwords configured)",
                System.currentTimeMillis() - start, true);
            return;
        }

        String[] tokens = input.trim().toLowerCase().split("\\s+");
        String cleaned = Arrays.stream(tokens)
            .filter(t -> !stopwords.contains(t))
            .collect(Collectors.joining(" "));

        long took = System.currentTimeMillis() - start;

        // Don't apply if result would be empty
        if (cleaned.isBlank()) {
            context.addTrace(name(), input, "skipped (would empty query)", took, true);
            return;
        }

        // Don't apply if nothing changed
        if (cleaned.equals(input.trim().toLowerCase())) {
            context.addTrace(name(), input, "no stopwords found", took, true);
            return;
        }

        context.setCurrentQuery(cleaned);
        log.debug("STOPWORD_REMOVAL '{}' -> '{}' took={}ms", input, cleaned, took);
        context.addTrace(name(), input, cleaned, took, false);
    }
}
