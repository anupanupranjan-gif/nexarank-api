-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0

CREATE TABLE click_events (
    id VARCHAR(50) PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    project_id VARCHAR(50) NOT NULL,
    session_id VARCHAR(100),
    query VARCHAR(500) NOT NULL,
    product_id VARCHAR(100),
    product_title VARCHAR(500),
    position INTEGER,
    clicked_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_click_events_tenant_project ON click_events(tenant_id, project_id);
CREATE INDEX idx_click_events_query ON click_events(query);
CREATE INDEX idx_click_events_clicked_at ON click_events(clicked_at DESC);
CREATE INDEX idx_click_events_session ON click_events(session_id);
