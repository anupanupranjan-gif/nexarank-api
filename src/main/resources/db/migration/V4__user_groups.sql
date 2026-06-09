-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0

-- User Groups
CREATE TABLE user_groups (
    id VARCHAR(50) PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL REFERENCES tenants(id),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, name)
);

-- Group Permissions
CREATE TABLE group_permissions (
    id VARCHAR(50) PRIMARY KEY,
    group_id VARCHAR(50) NOT NULL REFERENCES user_groups(id) ON DELETE CASCADE,
    permission VARCHAR(100) NOT NULL,
    UNIQUE(group_id, permission)
);

-- Add group_id to users
ALTER TABLE users ADD COLUMN group_id VARCHAR(50) REFERENCES user_groups(id);

-- Indexes
CREATE INDEX idx_user_groups_tenant ON user_groups(tenant_id);
CREATE INDEX idx_group_permissions_group ON group_permissions(group_id);
CREATE INDEX idx_users_group ON users(group_id);
