-- NR-152: ContentRule was never brought under NR-121's project-scoping model -
-- it only ever had tenant_id, so every content rule in a tenant was visible
-- and editable across every project in that tenant regardless of a caller's
-- project-scoped role (found while regression-testing NR-152, tracked
-- alongside NR-162/NR-163). Adds project_id the same way merch_rules already
-- has it (V1__init_schema.sql).
--
-- Backfill can't assume a fixed project id/name - only the seeded 'default'
-- tenant's project is literally named/id'd 'main' (V1__init_schema.sql); every
-- other tenant gets a randomly-id'd "Main" project auto-created at tenant
-- creation time (TenantController#createTenant). So each tenant's existing
-- content_rules rows are backfilled onto that tenant's own oldest project -
-- its de facto default - rather than a hardcoded id.
ALTER TABLE content_rules ADD COLUMN project_id VARCHAR(50) REFERENCES projects(id);
ALTER TABLE content_rule_versions ADD COLUMN project_id VARCHAR(50) REFERENCES projects(id);

UPDATE content_rules cr
SET project_id = (
    SELECT p.id FROM projects p
    WHERE p.tenant_id = cr.tenant_id
    ORDER BY p.created_at ASC
    LIMIT 1
)
WHERE cr.project_id IS NULL;

UPDATE content_rule_versions crv
SET project_id = (
    SELECT cr.project_id FROM content_rules cr WHERE cr.id = crv.rule_id
)
WHERE crv.project_id IS NULL;

ALTER TABLE content_rules ALTER COLUMN project_id SET NOT NULL;
ALTER TABLE content_rule_versions ALTER COLUMN project_id SET NOT NULL;

CREATE INDEX idx_content_rules_tenant_project ON content_rules(tenant_id, project_id);
