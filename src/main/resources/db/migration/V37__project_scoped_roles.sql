-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
-- NR-121 step 1: user_projects becomes the source of truth for the
-- genuinely project-scoped roles (MERCHANDISER, APPROVER, and the future
-- composite PROJECT_ADMIN = both roles on one project). ADMIN, VIEWER,
-- STAKEHOLDER (and the deprecated TENANT_ADMIN/SUPER_ADMIN) stay on
-- users.role, untouched by this migration.
--
-- Relax the unique constraint so a user can hold more than one role on the
-- same project.
ALTER TABLE user_projects DROP CONSTRAINT user_projects_user_id_project_id_key;
ALTER TABLE user_projects ADD CONSTRAINT user_projects_user_id_project_id_role_key UNIQUE (user_id, project_id, role);

-- One-time backfill bridge only. This does NOT tighten anyone's access:
-- every existing MERCHANDISER/APPROVER gets their current tenant-wide role
-- granted on EVERY project in their tenant, exactly preserving today's de
-- facto behavior (a tenant-wide role already sees every project). Actually
-- restricting a user to specific projects is a manual follow-up action for
-- later (via the new per-project assign/remove endpoints) — this migration
-- does not enforce scoping by itself; enforcement is a later step, once JWT
-- issuance is rewired (blocked on NR-120 landing first).
INSERT INTO user_projects (id, user_id, project_id, role)
SELECT gen_random_uuid()::text, u.id, p.id, u.role
FROM users u
JOIN projects p ON p.tenant_id = u.tenant_id
WHERE u.role IN ('MERCHANDISER', 'APPROVER')
ON CONFLICT (user_id, project_id, role) DO NOTHING;
