-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
-- NR-121 step 3: tracks which project a user last activated, so login and
-- a plain /auth/refresh (no explicit projectId) can resolve back to it
-- without the client having to carry project state on every call. Updated
-- on login and on every explicit project switch. Null means "never
-- resolved a project yet" (fresh account) - falls back to first-assigned.
ALTER TABLE users ADD COLUMN last_active_project_id VARCHAR(50) REFERENCES projects(id);
