-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
-- V18: Pipeline stage configuration per tenant/project
--
-- Stores which stages are enabled and in what order, scoped per project.
-- Stage ordering is constrained within groups (PRE_QUERY, RULE_APPLICATION, POST_QUERY).
-- Groups always run in order: PRE_QUERY → RULE_APPLICATION → POST_QUERY.
--
-- Seeded with defaults for all existing tenant/project combinations.
-- New projects get defaults inserted at creation time (application layer, 26a config API).

CREATE TABLE pipeline_stage_config (
    id           BIGSERIAL    PRIMARY KEY,
    tenant_id    VARCHAR(100) NOT NULL,
    project_id   VARCHAR(100) NOT NULL,
    stage_name   VARCHAR(100) NOT NULL,   -- matches PipelineStage.name()
    stage_group  VARCHAR(50)  NOT NULL,   -- PRE_QUERY | RULE_APPLICATION | POST_QUERY
    stage_order  INT          NOT NULL,   -- order within group (lower = earlier)
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_pipeline_stage UNIQUE (tenant_id, project_id, stage_name)
);

CREATE INDEX idx_pipeline_stage_tenant_project
    ON pipeline_stage_config (tenant_id, project_id);

-- Seed defaults for all existing tenant/project pairs.
-- Only RULE_APPLICATION seeded now; 26b/26c/26d will add their own stages.
INSERT INTO pipeline_stage_config
    (tenant_id, project_id, stage_name, stage_group, stage_order, enabled)
SELECT
    p.tenant_id,
    p.id        AS project_id,
    s.stage_name,
    s.stage_group,
    s.stage_order,
    TRUE
FROM projects p
CROSS JOIN (VALUES
    ('RULE_APPLICATION', 'RULE_APPLICATION', 10)
) AS s(stage_name, stage_group, stage_order)
ON CONFLICT (tenant_id, project_id, stage_name) DO NOTHING;
