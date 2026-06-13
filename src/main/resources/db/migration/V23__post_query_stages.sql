-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
-- V23: Seed Phase 26d post-query stages into pipeline_stage_config
--
-- PERSONALIZATION disabled by default — requires session click history to be meaningful.
-- DIVERSITY enabled by default — safe to apply to all queries.

INSERT INTO pipeline_stage_config
    (tenant_id, project_id, stage_name, stage_group, stage_order, enabled)
SELECT
    p.tenant_id,
    p.id AS project_id,
    s.stage_name,
    s.stage_group,
    s.stage_order,
    s.enabled
FROM projects p
CROSS JOIN (VALUES
    ('PERSONALIZATION', 'POST_QUERY', 10, FALSE),
    ('DIVERSITY',       'POST_QUERY', 20, TRUE)
) AS s(stage_name, stage_group, stage_order, enabled)
ON CONFLICT (tenant_id, project_id, stage_name) DO NOTHING;
