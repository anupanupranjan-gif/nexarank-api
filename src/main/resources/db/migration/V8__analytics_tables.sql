-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0

-- Zero result queries
CREATE TABLE zero_result_queries (
    id VARCHAR(50) PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    project_id VARCHAR(50) NOT NULL,
    query VARCHAR(500) NOT NULL,
    session_id VARCHAR(100),
    occurred_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_zrq_tenant_project ON zero_result_queries(tenant_id, project_id);
CREATE INDEX idx_zrq_query ON zero_result_queries(query);
CREATE INDEX idx_zrq_occurred_at ON zero_result_queries(occurred_at DESC);

-- Search quality evaluation results
CREATE TABLE quality_eval_results (
    id VARCHAR(50) PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    project_id VARCHAR(50) NOT NULL,
    run_at TIMESTAMP NOT NULL DEFAULT NOW(),
    ndcg_at_5 DOUBLE PRECISION,
    ndcg_at_10 DOUBLE PRECISION,
    mrr_at_10 DOUBLE PRECISION,
    queries_evaluated INTEGER,
    notes VARCHAR(500)
);

CREATE INDEX idx_sqr_tenant_project ON quality_eval_results(tenant_id, project_id);
CREATE INDEX idx_sqr_run_at ON quality_eval_results(run_at DESC);
