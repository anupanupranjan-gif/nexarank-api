-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
-- V48: NR-124 — Generic OpenAI-Compatible LLM Adapter. No change needed to
-- llm_config.provider itself (VARCHAR(50), no CHECK constraint — the new
-- OPENAI_COMPATIBLE enum value fits as-is). Adds custom_headers for the
-- optional extra-header case the ticket asks for (a provider needing
-- something beyond standard Bearer auth) — JSON object string, null for
-- every existing row/provider.

ALTER TABLE llm_config ADD COLUMN custom_headers TEXT;
