-- NR-129: per-tenant CORS allow-list. Comma-separated list of origins
-- (scheme+host+port, e.g. "http://localhost:3002") permitted to call
-- nexarank-api's public browser-facing endpoints (/rules/enrich,
-- /content/enrich) directly from a storefront frontend. Kept on the tenant
-- row (not application.yml) so adding a future tenant's origin is a data
-- change, not a redeploy.
ALTER TABLE tenants ADD COLUMN allowed_origins TEXT;

-- Seed AvinoShop's frontend dev origin — the active blocker this ticket
-- fixes (vite.config.ts server.port = 3002).
UPDATE tenants SET allowed_origins = 'http://localhost:3002' WHERE id = 'avinoshop';
