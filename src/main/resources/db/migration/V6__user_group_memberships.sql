-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0

-- Many-to-many user group memberships
CREATE TABLE user_group_memberships (
    id VARCHAR(50) PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    group_id VARCHAR(50) NOT NULL REFERENCES user_groups(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, group_id)
);

CREATE INDEX idx_ugm_user ON user_group_memberships(user_id);
CREATE INDEX idx_ugm_group ON user_group_memberships(group_id);

-- Migrate existing single group_id to memberships table
INSERT INTO user_group_memberships (id, user_id, group_id)
SELECT gen_random_uuid()::text, id, group_id
FROM users
WHERE group_id IS NOT NULL;
