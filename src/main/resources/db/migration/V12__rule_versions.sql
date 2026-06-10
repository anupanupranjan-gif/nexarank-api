-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
-- Phase 22 / NR-33: Rule versioning. Non-destructive history, one row per snapshot.

CREATE TABLE rule_versions (
    id              VARCHAR(36)  PRIMARY KEY,
    rule_id         VARCHAR(36)  NOT NULL,
    tenant_id       VARCHAR(64)  NOT NULL,
    project_id      VARCHAR(64)  NOT NULL,
    version_number  INT          NOT NULL,
    snapshot        TEXT         NOT NULL,
    changed_by      VARCHAR(128),
    changed_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    change_note     VARCHAR(512),
    CONSTRAINT uq_rule_version UNIQUE (rule_id, version_number)
);

CREATE INDEX idx_rule_versions_rule_id ON rule_versions (rule_id);
CREATE INDEX idx_rule_versions_tenant ON rule_versions (tenant_id, project_id);
