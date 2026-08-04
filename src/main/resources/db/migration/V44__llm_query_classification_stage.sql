-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
-- V44: Seed LLM_QUERY_CLASSIFICATION stage into pipeline_stage_config (NR-56).
-- Disabled by default — this is the "per-project toggle" between rule-based
-- and LLM-based classification. QUERY_CLASSIFICATION (rule-based) always runs
-- first at order=30 and always sets a value; this stage runs right after at
-- order=31 and only overwrites it when enabled and the LLM call succeeds, so
-- leaving it disabled reproduces today's pure rule-based behavior exactly.

INSERT INTO pipeline_stage_config
    (tenant_id, project_id, stage_name, stage_group, stage_order, enabled)
SELECT
    p.tenant_id,
    p.id AS project_id,
    'LLM_QUERY_CLASSIFICATION',
    'PRE_QUERY',
    31,
    FALSE   -- disabled by default, enable from UI once LLM config is tested
FROM projects p
ON CONFLICT (tenant_id, project_id, stage_name) DO NOTHING;
