-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
-- V24: Add synonym_direction to merch_rules
--
-- TWO_WAY (default): "battery" ↔ "12v battery" — searching either finds both
-- ONE_WAY: "car battery" → "battery" — searching "car battery" also searches
--          "battery" but NOT vice versa. Useful for brand/product aliases.

ALTER TABLE merch_rules
    ADD COLUMN synonym_direction VARCHAR(20) NOT NULL DEFAULT 'TWO_WAY';
