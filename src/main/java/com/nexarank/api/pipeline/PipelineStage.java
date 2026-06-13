// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.pipeline;

/**
 * Single stage in the NexaRank query pipeline.
 *
 * Groups always execute in order: PRE_QUERY → RULE_APPLICATION → POST_QUERY
 * Within each group, stages can be reordered per project via pipeline_stage_config.
 *
 * To add a new stage: implement this interface, annotate @Component. Done.
 */
public interface PipelineStage {

    /** Unique identifier used for config, tracing, and the UI. SCREAMING_SNAKE_CASE. */
    String name();

    /** Which execution group this stage belongs to. */
    StageGroup group();

    /** Default order within the group. Overridable per project in DB. */
    int defaultOrder();

    /**
     * Execute the stage. Mutates context in place.
     * Must not throw — catch internally and degrade gracefully.
     * Must call context.addTrace() before returning.
     */
    void execute(PipelineContext context);

    enum StageGroup {
        PRE_QUERY,         // spell correction, stopwords, synonyms, LLM rewrite, classification
        RULE_APPLICATION,  // rule lookup, A/B resolution, conflict resolution
        POST_QUERY         // personalization, filtering, diversity (26d)
    }
}
