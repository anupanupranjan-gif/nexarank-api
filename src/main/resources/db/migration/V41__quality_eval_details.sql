-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
-- NR-121: SearchQualityService previously cached its full result (including
-- the by-mode/by-intent breakdown) in a single in-memory field shared across
-- every tenant and project — one ADMIN's "Run" overwrote what everyone,
-- anywhere, saw. quality_eval_results already existed, tenant+project
-- scoped, but nothing ever wrote to it. This column lets the full detailed
-- result (not just the ndcg/mrr summary columns already on this table)
-- persist per tenant+project, same JSON-backed-column pattern used
-- elsewhere in this schema (merch_rules.pinned_ids_json etc.).
ALTER TABLE quality_eval_results ADD COLUMN details_json TEXT;
