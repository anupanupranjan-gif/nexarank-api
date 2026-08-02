-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
-- NR-121 step 2 bugfix: user_projects' original FKs (V1) had no ON DELETE
-- CASCADE, unlike the equivalent user_group_memberships table which does.
-- This went undetected because user_projects was always empty until V37's
-- write path started actually populating it — deleting a user with
-- project-role rows now throws a foreign key violation instead of cleanly
-- removing their assignments alongside them.
ALTER TABLE user_projects DROP CONSTRAINT user_projects_user_id_fkey;
ALTER TABLE user_projects ADD CONSTRAINT user_projects_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE user_projects DROP CONSTRAINT user_projects_project_id_fkey;
ALTER TABLE user_projects ADD CONSTRAINT user_projects_project_id_fkey
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE;
