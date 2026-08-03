-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
-- NR-65: invite-by-email, password reset, and email verification all need
-- the same shape (a single-use, expiring, hashed token tied to a user) —
-- one generic table with a purpose column rather than three near-identical
-- ones, mirroring refresh_tokens' own hash-at-rest pattern.

ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE;
-- Default TRUE grandfathers every existing account (and any future raw-SQL
-- insert) — new accounts created via the application (invite or direct
-- create-with-email) explicitly set this to FALSE, since login now blocks
-- on it. An account with no email is treated as verified (nothing to verify).

CREATE TABLE user_action_tokens (
    id VARCHAR(50) PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    purpose VARCHAR(30) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_user_action_tokens_hash ON user_action_tokens(token_hash);
CREATE INDEX idx_user_action_tokens_user ON user_action_tokens(user_id, purpose);
