-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
-- V22: Seed LLM_QUERY_REWRITE stage into pipeline_stage_config
-- Disabled by default — admin enables it per project once LLM config is verified.

INSERT INTO pipeline_stage_config
    (tenant_id, project_id, stage_name, stage_group, stage_order, enabled)
SELECT
    p.tenant_id,
    p.id AS project_id,
    'LLM_QUERY_REWRITE',
    'PRE_QUERY',
    40,
    FALSE   -- disabled by default, enable from UI once LLM config is tested
FROM projects p
ON CONFLICT (tenant_id, project_id, stage_name) DO NOTHING;
