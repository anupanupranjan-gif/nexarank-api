-- NR-82: Phase 28 Experience Manager foundation — content rules drive
-- page content zones (banners, promo grids) the same way merch_rules
-- drive search ranking.
CREATE TABLE content_rules (
    id VARCHAR(50) PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL REFERENCES tenants(id),
    zone VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    priority INT NOT NULL DEFAULT 50,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    schedule_start TIMESTAMP,
    schedule_end TIMESTAMP,
    trigger_conditions TEXT,
    content_payload TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100)
);

CREATE INDEX idx_content_rules_tenant_zone ON content_rules(tenant_id, zone);
CREATE INDEX idx_content_rules_status ON content_rules(status);
