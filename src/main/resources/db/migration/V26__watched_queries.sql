-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
-- V26: Watched queries — high-priority queries with expected performance thresholds
--
-- Merchandisers define queries they care about and set CTR/position expectations.
-- AiRuleSuggestionService alerts when actual performance falls below thresholds.

CREATE TABLE watched_queries (
    id                      VARCHAR(36)      PRIMARY KEY,
    tenant_id               VARCHAR(100)     NOT NULL,
    project_id              VARCHAR(100)     NOT NULL,
    query                   VARCHAR(500)     NOT NULL,
    expected_min_ctr        DOUBLE PRECISION,   -- alert if CTR drops below this
    expected_max_position   DOUBLE PRECISION,   -- alert if avg position rises above this
    notes                   VARCHAR(500),        -- why this query is being watched
    enabled                 BOOLEAN          NOT NULL DEFAULT TRUE,
    created_by              VARCHAR(100),
    created_at              TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ      NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_watched_query UNIQUE (tenant_id, project_id, query)
);

CREATE INDEX idx_watched_queries_tenant_project
    ON watched_queries (tenant_id, project_id);
