-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
-- NR-36: capture which facets were selected on a search, to back facet usage
-- reporting (click rates, value selection frequency, unused facets).
ALTER TABLE search_events ADD COLUMN selected_facets TEXT;
