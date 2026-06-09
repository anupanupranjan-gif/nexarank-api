-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0

CREATE TABLE search_events (
    id VARCHAR(50) PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    project_id VARCHAR(50) NOT NULL,
    session_id VARCHAR(100),
    query VARCHAR(500) NOT NULL,
    result_count INTEGER NOT NULL DEFAULT 0,
    mode VARCHAR(20),
    took_ms INTEGER,
    searched_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_search_events_tenant_project ON search_events(tenant_id, project_id);
CREATE INDEX idx_search_events_query ON search_events(query);
CREATE INDEX idx_search_events_searched_at ON search_events(searched_at DESC);
CREATE INDEX idx_search_events_result_count ON search_events(result_count);
