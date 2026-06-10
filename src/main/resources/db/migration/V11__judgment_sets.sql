-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0

-- Judgment sets (named collections of relevance judgments)
CREATE TABLE judgment_sets (
    id VARCHAR(50) PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    project_id VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, project_id, name)
);

-- Individual judgments (query + product + grade)
CREATE TABLE judgments (
    id VARCHAR(50) PRIMARY KEY,
    set_id VARCHAR(50) NOT NULL REFERENCES judgment_sets(id) ON DELETE CASCADE,
    query VARCHAR(500) NOT NULL,
    product_id VARCHAR(100) NOT NULL,
    product_title VARCHAR(500),
    grade INTEGER NOT NULL DEFAULT 0 CHECK (grade >= 0 AND grade <= 3),
    judged_by VARCHAR(100),
    judged_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(set_id, query, product_id)
);

CREATE INDEX idx_judgment_sets_tenant_project ON judgment_sets(tenant_id, project_id);
CREATE INDEX idx_judgments_set_query ON judgments(set_id, query);
