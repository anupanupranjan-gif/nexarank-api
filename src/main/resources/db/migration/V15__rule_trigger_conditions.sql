-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
-- Phase 23 / NR-37 v2: Facet-based rule triggers (multi-condition model).
-- Drops the single-facet columns from V14 and replaces with a proper conditions table.

-- Drop V14 trigger columns from merch_rules
ALTER TABLE merch_rules
    DROP COLUMN IF EXISTS trigger_type,
    DROP COLUMN IF EXISTS trigger_facet_field,
    DROP COLUMN IF EXISTS trigger_facet_value;

-- Add require_query flag: true = rule only fires when query matches,
-- false = rule fires on any query (blank/wildcard pages)
ALTER TABLE merch_rules
    ADD COLUMN IF NOT EXISTS require_query BOOLEAN NOT NULL DEFAULT true;

-- New conditions table: one row per facet condition per rule
-- Multiple conditions = AND logic
-- facet_values is a JSON array: ["Battery","Automotive"] = OR within the facet
CREATE TABLE rule_trigger_conditions (
    id           VARCHAR(36)   PRIMARY KEY,
    rule_id      VARCHAR(36)   NOT NULL REFERENCES merch_rules(id) ON DELETE CASCADE,
    tenant_id    VARCHAR(64)   NOT NULL,
    project_id   VARCHAR(64)   NOT NULL,
    facet_field  VARCHAR(100)  NOT NULL,
    facet_values TEXT          NOT NULL,  -- JSON array e.g. ["Battery","Automotive"]
    position     INT           NOT NULL DEFAULT 0
);

CREATE INDEX idx_rtc_rule_id ON rule_trigger_conditions (rule_id);
CREATE INDEX idx_rtc_tenant  ON rule_trigger_conditions (tenant_id, project_id);
