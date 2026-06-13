-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
-- Phase 24 / NR-38: Conditional Facet Visibility.

CREATE TABLE facet_visibility_rules (
    id                   VARCHAR(36)   PRIMARY KEY,
    tenant_id            VARCHAR(64)   NOT NULL,
    project_id           VARCHAR(64)   NOT NULL,
    name                 VARCHAR(255)  NOT NULL,
    trigger_facet_field  VARCHAR(100)  NOT NULL,
    trigger_facet_value  VARCHAR(255)  NOT NULL,
    show_facets          TEXT          NOT NULL DEFAULT '[]',  -- JSON array of fieldNames
    hide_facets          TEXT          NOT NULL DEFAULT '[]',  -- JSON array of fieldNames
    priority             INT           NOT NULL DEFAULT 50,
    enabled              BOOLEAN       NOT NULL DEFAULT true,
    created_by           VARCHAR(128),
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_fvr_tenant     ON facet_visibility_rules (tenant_id, project_id);
CREATE INDEX idx_fvr_trigger    ON facet_visibility_rules (trigger_facet_field, trigger_facet_value);
CREATE INDEX idx_fvr_enabled    ON facet_visibility_rules (enabled);

-- Seed battery-specific facets for demo
-- These fields may not exist in the ES index yet but will return empty values gracefully
INSERT INTO facet_config (id, tenant_id, project_id, field_name, display_label, facet_type, enabled, show_count, sort_order, created_at, updated_at)
VALUES
  (gen_random_uuid()::text, 'default', 'main', 'voltage',         'Voltage',           'TERMS',   false, true,  10, now(), now()),
  (gen_random_uuid()::text, 'default', 'main', 'cranking_amps',   'Cranking Amps',     'RANGE',   false, false, 11, now(), now()),
  (gen_random_uuid()::text, 'default', 'main', 'battery_type',    'Battery Type',      'TERMS',   false, true,  12, now(), now()),
  (gen_random_uuid()::text, 'default', 'main', 'viscosity_grade', 'Viscosity Grade',   'TERMS',   false, true,  20, now(), now()),
  (gen_random_uuid()::text, 'default', 'main', 'oil_type',        'Oil Type',          'TERMS',   false, true,  21, now(), now()),
  (gen_random_uuid()::text, 'default', 'main', 'thread_size',     'Thread Size',       'TERMS',   false, true,  30, now(), now()),
  (gen_random_uuid()::text, 'default', 'main', 'material',        'Material',          'TERMS',   false, true,  31, now(), now())
ON CONFLICT DO NOTHING;
