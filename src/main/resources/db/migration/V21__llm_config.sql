-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
-- V21: LLM provider configuration per tenant/project
--
-- One row per tenant/project. Stores connection details for the customer's
-- chosen LLM provider (Ollama, OpenAI, Azure OpenAI, Anthropic, Cohere).
-- Managed from the NexaRank admin UI — Settings → LLM Configuration.

CREATE TABLE llm_config (
    id                  VARCHAR(36)  PRIMARY KEY,
    tenant_id           VARCHAR(100) NOT NULL,
    project_id          VARCHAR(100) NOT NULL,
    provider            VARCHAR(50)  NOT NULL,   -- OLLAMA | OPENAI | AZURE_OPENAI | ANTHROPIC | COHERE
    endpoint            VARCHAR(500) NOT NULL,
    api_key             VARCHAR(500),
    model               VARCHAR(200) NOT NULL,
    timeout_seconds     INT          NOT NULL DEFAULT 5,
    prompt_template     TEXT,                    -- NULL means use default
    last_status         VARCHAR(20)  NOT NULL DEFAULT 'UNTESTED',
    last_status_message VARCHAR(500),
    last_tested_at      TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_llm_config UNIQUE (tenant_id, project_id)
);

-- Seed default Ollama config for existing tenant/project pairs.
-- Points to host gateway (172.17.0.1) which is reachable from Kind pods on Linux.
-- Admin can update endpoint and model from the UI after first deploy.
INSERT INTO llm_config (id, tenant_id, project_id, provider, endpoint, model, timeout_seconds)
SELECT
    gen_random_uuid()::text,
    p.tenant_id,
    p.id AS project_id,
    'OLLAMA',
    'http://172.17.0.1:11434',
    'gemma3:1b',
    5
FROM projects p
ON CONFLICT (tenant_id, project_id) DO NOTHING;
