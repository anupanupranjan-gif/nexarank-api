-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
-- Phase 25 / NR-41: Rule Preview URL in Engine Config.
ALTER TABLE engine_config
    ADD COLUMN IF NOT EXISTS preview_url VARCHAR(500);
