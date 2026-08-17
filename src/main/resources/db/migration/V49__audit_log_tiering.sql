-- NR-70: Audit Log Hardening — Tier 1 / Tier 2 split, SOC 2 aligned.
--
-- Tier 1 = rule-change history (MERCHANDISER and above, project-scoped).
-- Tier 2 = full audit log (ADMIN only) — approvals with reason, API access,
--          failed authentication. Security-posture data, least privilege.
--
-- Append-only by design: no UPDATE/DELETE API is exposed for either store.
-- The only deletion path is the scheduled retention purge (AuditRetentionService),
-- governed by tenants.audit_retention_days below.

-- ── Tier 1 enrichment on the existing audit_events table ────────────────────
ALTER TABLE audit_events ADD COLUMN tier INT NOT NULL DEFAULT 2;

-- Denormalized at write time (NOT a read-time join) so an entry reads
-- "Priority changed for 'BOOST battery'" rather than a bare rule id, and
-- still reads correctly after the underlying rule is deleted.
ALTER TABLE audit_events ADD COLUMN entity_name VARCHAR(500);

ALTER TABLE audit_events ADD COLUMN previous_state VARCHAR(100);
ALTER TABLE audit_events ADD COLUMN new_state VARCHAR(100);

-- Structural field-level diff as JSON: [{"field":"priority","oldValue":"5","newValue":"10"}]
ALTER TABLE audit_events ADD COLUMN field_diff TEXT;

-- Approval/rejection reason (Tier 2 requirement; also shown on Tier 1 rule rows).
ALTER TABLE audit_events ADD COLUMN reason TEXT;

-- Existing rule lifecycle events are Tier 1 retroactively; everything else
-- (user creation, engine config, facet changes) stays Tier 2.
UPDATE audit_events SET tier = 1 WHERE action LIKE 'RULE\_%';

CREATE INDEX idx_audit_events_tier ON audit_events (tenant_id, tier, created_at DESC);
CREATE INDEX idx_audit_events_project ON audit_events (tenant_id, project_id, created_at DESC);

-- ── Tier 2: API access log + failed authentication ──────────────────────────
-- Separate table rather than more rows in audit_events: this is high-volume
-- request-rate data with a different shape and different retention pressure,
-- and mixing it in would swamp the rule-change history it sits next to.
CREATE TABLE api_access_events (
    id              VARCHAR(36)  PRIMARY KEY,
    tenant_id       VARCHAR(100) NOT NULL,
    project_id      VARCHAR(100),
    user_id         VARCHAR(36),
    username        VARCHAR(255),
    event_type      VARCHAR(30)  NOT NULL,  -- API_ACCESS | AUTH_FAILURE
    endpoint        VARCHAR(500) NOT NULL,
    http_method     VARCHAR(10),
    params          TEXT,
    response_code   INT,
    latency_ms      BIGINT,
    ip_address      VARCHAR(64),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_api_access_tenant ON api_access_events (tenant_id, created_at DESC);
CREATE INDEX idx_api_access_type ON api_access_events (tenant_id, event_type, created_at DESC);

-- ── Configurable retention per tenant ───────────────────────────────────────
-- Default 90 days; enterprise tenants set 365 via PUT /admin/tenants/{id}.
ALTER TABLE tenants ADD COLUMN audit_retention_days INT NOT NULL DEFAULT 90;
