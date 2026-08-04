-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
-- V45: NR-57 — LLM Auto-generated synonym suggestions, widened from
-- zero-result-only to live queries. LlmSynonymSuggestionStage (new PRE_QUERY
-- stage) tracks a lightweight frequency counter per query here, deliberately
-- WITHOUT calling the LLM inline on every live search — real LLM synonym
-- generation happens later, on-demand, only for queries a merchandiser
-- actually opens for review (AiRuleSuggestionService.suggestSynonymsForLiveQueries).

CREATE TABLE live_query_synonym_candidates (
    id            BIGSERIAL    PRIMARY KEY,
    tenant_id     VARCHAR(100) NOT NULL,
    project_id    VARCHAR(100) NOT NULL,
    query         VARCHAR(500) NOT NULL,
    hit_count     INT          NOT NULL DEFAULT 1,
    first_seen_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_seen_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_live_query_candidate UNIQUE (tenant_id, project_id, query)
);

CREATE INDEX idx_live_query_candidates_lookup
    ON live_query_synonym_candidates (tenant_id, project_id, hit_count DESC);

-- Seed LLM_SYNONYM_SUGGESTION stage into pipeline_stage_config, disabled by
-- default — same opt-in convention as LLM_QUERY_REWRITE (V22) and
-- LLM_QUERY_CLASSIFICATION (V44).
INSERT INTO pipeline_stage_config
    (tenant_id, project_id, stage_name, stage_group, stage_order, enabled)
SELECT
    p.tenant_id,
    p.id AS project_id,
    'LLM_SYNONYM_SUGGESTION',
    'PRE_QUERY',
    35,
    FALSE
FROM projects p
ON CONFLICT (tenant_id, project_id, stage_name) DO NOTHING;
