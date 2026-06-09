-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0
ALTER TABLE merch_rules ADD COLUMN IF NOT EXISTS priority INTEGER NOT NULL DEFAULT 50;
CREATE INDEX idx_merch_rules_priority ON merch_rules(tenant_id, project_id, priority);
