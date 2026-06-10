-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
-- Phase 22 / NR-32: A/B testing for rule variants.

CREATE TABLE rule_ab_tests (
    id              VARCHAR(36)     PRIMARY KEY,
    tenant_id       VARCHAR(64)     NOT NULL,
    project_id      VARCHAR(64)     NOT NULL,
    query           VARCHAR(500)    NOT NULL,
    rule_a_id       VARCHAR(36)     NOT NULL,
    rule_b_id       VARCHAR(36)     NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'RUNNING',  -- RUNNING, COMPLETED, ARCHIVED
    impressions_a   BIGINT          NOT NULL DEFAULT 0,
    impressions_b   BIGINT          NOT NULL DEFAULT 0,
    clicks_a        BIGINT          NOT NULL DEFAULT 0,
    clicks_b        BIGINT          NOT NULL DEFAULT 0,
    winner_id       VARCHAR(36),    -- rule_id of winner after promotion
    created_by      VARCHAR(128),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    CONSTRAINT uq_ab_test_query UNIQUE (tenant_id, project_id, query, status)
        DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX idx_ab_tests_tenant    ON rule_ab_tests (tenant_id, project_id);
CREATE INDEX idx_ab_tests_query     ON rule_ab_tests (query);
CREATE INDEX idx_ab_tests_status    ON rule_ab_tests (status);

-- Add variant tracking to click events
ALTER TABLE click_events ADD COLUMN IF NOT EXISTS variant_id VARCHAR(36);
CREATE INDEX IF NOT EXISTS idx_click_events_variant ON click_events (variant_id);
