-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
-- V47: NR-59 — LLM Zero-Result Recovery. Real architecture gap found before
-- writing this: nexarank-api's /rules/enrich is called by search-api BEFORE
-- the ES query runs, so no pipeline stage here can ever see whether results
-- came back empty ("POST_QUERY group, runs when results list is empty" per
-- the ticket text isn't something QueryPipelineOrchestrator can do — it has
-- no results in PipelineContext at all, see the 2026-08-01 NR-108 audit
-- comment). Recovery is therefore driven by search-api itself, calling a new
-- stateless nexarank-api suggestion endpoint synchronously after its own
-- zero-hit search, then retrying with the suggested query. This migration
-- only adds the toggle (so it's still controllable from the Pipeline Editor,
-- same convention as every other LLM stage) and the analytics columns.

ALTER TABLE zero_result_queries ADD COLUMN suggested_query VARCHAR(500);
ALTER TABLE zero_result_queries ADD COLUMN recovered BOOLEAN NOT NULL DEFAULT FALSE;

-- Seed ZERO_RESULT_RECOVERY into pipeline_stage_config, disabled by default —
-- same opt-in convention as LLM_QUERY_REWRITE (V22), LLM_QUERY_CLASSIFICATION
-- (V44), LLM_SYNONYM_SUGGESTION (V45). No PipelineStage bean is registered
-- for this name (it's never executed inside QueryPipelineOrchestrator's own
-- loop, for the reason above) — the row exists purely so the merchandiser-
-- facing toggle in Pipeline Editor and PipelineStageConfigService.isEnabled()
-- have something to read.
INSERT INTO pipeline_stage_config
    (tenant_id, project_id, stage_name, stage_group, stage_order, enabled)
SELECT
    p.tenant_id,
    p.id AS project_id,
    'ZERO_RESULT_RECOVERY',
    'POST_QUERY',
    30,
    FALSE
FROM projects p
ON CONFLICT (tenant_id, project_id, stage_name) DO NOTHING;
