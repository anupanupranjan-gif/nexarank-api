-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
-- NR-120: revocable refresh tokens backing short-lived access tokens.
-- Session/device identity only, deliberately no project context per the
-- NR-121 reconciliation (a project switch shouldn't spawn a new "session").
-- The raw token is never stored, only its SHA-256 hash. revoked_at is a
-- soft marker (not a row delete) so a session's history stays visible to
-- an admin, matching this codebase's existing disabled-not-deleted
-- convention elsewhere (MerchRule, ContentRule).
CREATE TABLE refresh_tokens (
    id VARCHAR(50) PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tenant_id VARCHAR(50) NOT NULL REFERENCES tenants(id),
    token_hash VARCHAR(64) NOT NULL,
    device_info VARCHAR(255),
    ip_address VARCHAR(64),
    issued_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,
    last_used_at TIMESTAMP,
    revoked_at TIMESTAMP
);

CREATE UNIQUE INDEX idx_refresh_tokens_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_tenant ON refresh_tokens(tenant_id);
