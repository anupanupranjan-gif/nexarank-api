-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
-- V19: Seed Phase 26b pre-query stages into pipeline_stage_config
--
-- Adds STOPWORD_REMOVAL, SPELL_CORRECTION, and QUERY_CLASSIFICATION
-- for all existing tenant/project pairs, enabled by default.
-- Stage order matches PipelineStage.defaultOrder() values.

INSERT INTO pipeline_stage_config
    (tenant_id, project_id, stage_name, stage_group, stage_order, enabled)
SELECT
    p.tenant_id,
    p.id AS project_id,
    s.stage_name,
    s.stage_group,
    s.stage_order,
    TRUE
FROM projects p
CROSS JOIN (VALUES
    ('STOPWORD_REMOVAL',    'PRE_QUERY', 10),
    ('SPELL_CORRECTION',    'PRE_QUERY', 20),
    ('QUERY_CLASSIFICATION','PRE_QUERY', 30)
) AS s(stage_name, stage_group, stage_order)
ON CONFLICT (tenant_id, project_id, stage_name) DO NOTHING;
