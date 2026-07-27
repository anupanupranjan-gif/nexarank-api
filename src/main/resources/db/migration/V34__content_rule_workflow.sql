-- NR-84: maker-checker workflow + versioning for content rules, same shape
-- as merch_rules' approval trail (submitted_by/approved_by/rejection_comment)
-- and rule_versions (append-only per-rule snapshot history).
ALTER TABLE content_rules ADD COLUMN IF NOT EXISTS submitted_by VARCHAR(100);
ALTER TABLE content_rules ADD COLUMN IF NOT EXISTS approved_by VARCHAR(100);
ALTER TABLE content_rules ADD COLUMN IF NOT EXISTS rejection_comment TEXT;
ALTER TABLE content_rules ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

CREATE TABLE content_rule_versions (
    id VARCHAR(50) PRIMARY KEY,
    rule_id VARCHAR(50) NOT NULL,
    tenant_id VARCHAR(50) NOT NULL,
    version_number INT NOT NULL,
    snapshot TEXT NOT NULL,
    changed_by VARCHAR(100),
    changed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    change_note VARCHAR(255),
    CONSTRAINT uq_content_rule_version UNIQUE (rule_id, version_number)
);

CREATE INDEX idx_content_rule_versions_rule_id ON content_rule_versions(rule_id);
