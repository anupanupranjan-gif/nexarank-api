-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS logo_url VARCHAR(500);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS brand_color VARCHAR(7) DEFAULT '#0077ff';
