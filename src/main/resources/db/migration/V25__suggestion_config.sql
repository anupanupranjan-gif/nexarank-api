-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
-- V25: Configurable thresholds for AI rule suggestions per tenant/project
--
-- Replaces hardcoded values in AiRuleSuggestionService:
--   avgPos >= 4.0       -> max_click_position
--   clicks >= 1         -> min_clicks
--   (new) min_ctr       -> minimum CTR before suggesting boost
--   (new) min_impressions -> reduce noise from low-traffic queries

CREATE TABLE suggestion_config (
    id                  VARCHAR(36)     PRIMARY KEY,
    tenant_id           VARCHAR(100)    NOT NULL,
    project_id          VARCHAR(100)    NOT NULL,
    min_ctr             DOUBLE PRECISION NOT NULL DEFAULT 0.05,
    max_click_position  DOUBLE PRECISION NOT NULL DEFAULT 4.0,
    min_clicks          INT             NOT NULL DEFAULT 1,
    min_impressions     INT             NOT NULL DEFAULT 5,
    lookback_days       INT             NOT NULL DEFAULT 30,
    max_suggestions     INT             NOT NULL DEFAULT 10,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_suggestion_config UNIQUE (tenant_id, project_id)
);

-- Seed defaults for all existing tenant/project pairs
INSERT INTO suggestion_config (id, tenant_id, project_id)
SELECT
    gen_random_uuid()::text,
    p.tenant_id,
    p.id AS project_id
FROM projects p
ON CONFLICT (tenant_id, project_id) DO NOTHING;
