-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0

-- Tenants
CREATE TABLE tenants (
    id VARCHAR(50) PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Projects
CREATE TABLE projects (
    id VARCHAR(50) PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL REFERENCES tenants(id),
    name VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Users
CREATE TABLE users (
    id VARCHAR(50) PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL REFERENCES tenants(id),
    username VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, username)
);

-- User Project Roles
CREATE TABLE user_projects (
    id VARCHAR(50) PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL REFERENCES users(id),
    project_id VARCHAR(50) NOT NULL REFERENCES projects(id),
    role VARCHAR(50) NOT NULL,
    UNIQUE(user_id, project_id)
);

-- Merchandising Rules
CREATE TABLE merch_rules (
    id VARCHAR(50) PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL REFERENCES tenants(id),
    project_id VARCHAR(50) NOT NULL REFERENCES projects(id),
    type VARCHAR(50) NOT NULL,
    query VARCHAR(500) NOT NULL,
    boost_field VARCHAR(100),
    boost_value VARCHAR(255),
    boost_factor FLOAT,
    pinned_ids TEXT,
    synonyms TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    submitted_by VARCHAR(100),
    approved_by VARCHAR(100),
    rejection_comment TEXT,
    activate_at TIMESTAMP,
    expire_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Facet Config
CREATE TABLE facet_config (
    id VARCHAR(50) PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL REFERENCES tenants(id),
    project_id VARCHAR(50) NOT NULL REFERENCES projects(id),
    field_name VARCHAR(100) NOT NULL,
    display_label VARCHAR(255),
    facet_type VARCHAR(50) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    show_count BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    max_values INTEGER,
    range_min FLOAT,
    range_max FLOAT,
    range_interval FLOAT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Engine Config
CREATE TABLE engine_config (
    id VARCHAR(50) PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL REFERENCES tenants(id),
    project_id VARCHAR(50) NOT NULL REFERENCES projects(id),
    engine_type VARCHAR(50) NOT NULL,
    host VARCHAR(255),
    port INTEGER,
    scheme VARCHAR(10) DEFAULT 'https',
    index_name VARCHAR(255),
    username VARCHAR(100),
    password VARCHAR(255),
    ssl_enabled BOOLEAN DEFAULT TRUE,
    ssl_verify BOOLEAN DEFAULT FALSE,
    last_status VARCHAR(50) DEFAULT 'UNTESTED',
    last_status_message VARCHAR(500),
    last_tested_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Audit Events
CREATE TABLE audit_events (
    id VARCHAR(50) PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    project_id VARCHAR(50),
    user_id VARCHAR(50),
    username VARCHAR(100),
    action VARCHAR(100) NOT NULL,
    entity VARCHAR(100),
    entity_id VARCHAR(50),
    details TEXT,
    ip_address VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Seed default tenant and project
INSERT INTO tenants (id, display_name) VALUES ('default', 'Default');
INSERT INTO projects (id, tenant_id, name) VALUES ('main', 'default', 'Main');

-- Seed default admin user (password: admin123, bcrypt hashed)
INSERT INTO users (id, tenant_id, username, password) 
VALUES ('admin', 'default', 'admin', '$2a$10$EiHdtxpatkj5lhSgBx/phOzeHoiPJkK1X9v3v8wl8mJWE5tpeGfJ6');

-- Assign admin to main project with ADMIN role
INSERT INTO user_projects (id, user_id, project_id, role)
VALUES ('admin-main', 'admin', 'main', 'ADMIN');

-- Indexes
CREATE INDEX idx_merch_rules_tenant_project ON merch_rules(tenant_id, project_id);
CREATE INDEX idx_merch_rules_query ON merch_rules(query);
CREATE INDEX idx_merch_rules_status ON merch_rules(status);
CREATE INDEX idx_facet_config_tenant_project ON facet_config(tenant_id, project_id);
CREATE INDEX idx_audit_events_tenant ON audit_events(tenant_id, created_at DESC);
CREATE INDEX idx_users_tenant ON users(tenant_id);
