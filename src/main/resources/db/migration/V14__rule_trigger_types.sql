-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
-- Phase 23 / NR-37: Facet-based rule triggers.

ALTER TABLE merch_rules
    ADD COLUMN IF NOT EXISTS trigger_type        VARCHAR(30)  NOT NULL DEFAULT 'QUERY_ONLY',
    ADD COLUMN IF NOT EXISTS trigger_facet_field VARCHAR(100),
    ADD COLUMN IF NOT EXISTS trigger_facet_value VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_merch_rules_trigger_type
    ON merch_rules (trigger_type);
