-- NR-88: REDIRECT rule type
ALTER TABLE merch_rules ADD COLUMN IF NOT EXISTS redirect_url VARCHAR(2048);
