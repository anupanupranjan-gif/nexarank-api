// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.port;

import com.nexarank.api.model.EnrichedQuery;
import com.nexarank.api.model.MerchRule;
import com.nexarank.api.model.SearchEngineConfig;
import com.nexarank.api.model.SearchField;

import java.util.List;

/**
 * Engine-agnostic port for all search engine interactions.
 *
 * Two responsibilities:
 * 1. Introspection — fetch fields, sample values for NexaRank admin UI
 * 2. Rule translation — convert NexaRank rules to engine-specific DSL
 *
 * Implementations: ElasticsearchAdapter, SolrAdapter
 * Selected at runtime based on SearchEngineConfig.engineType
 *
 * Contract, same spirit as LlmPort's "must never throw" but not identical in
 * shape: introspection methods (testConnection/getFields/getFieldValues)
 * must never throw at all — on failure they degrade to false/an empty list,
 * exactly as they already do, since callers (admin UI dropdowns) have no
 * meaningful fallback beyond "nothing to show." translateRules() is
 * different — it runs on the query hot path and a caller that only gets an
 * EnrichedQuery back has no way to tell "rules applied" from "translation
 * silently produced nothing." So instead: an implementation must never let a
 * raw/implementation-specific exception (IOException, NullPointerException,
 * etc.) escape translateRules() untranslated — on failure it must throw
 * RuleTranslationException, so every caller sees one well-known exception
 * type regardless of adapter or root cause. (RuleApplicationStage, the only
 * caller today, already catches broadly and degrades to a passthrough
 * result regardless of exception type — this contract is about the type
 * being meaningful to any caller, not about suppressing the failure.)
 */
public interface SearchEnginePort {

    /**
     * Test connectivity to the configured search engine.
     * Returns true if reachable and authenticated.
     */
    boolean testConnection(SearchEngineConfig config);

    /**
     * Get the list of fields in the search index.
     * Used in NexaRank admin UI for:
     * - Facet configuration field dropdowns
     * - Rule boost/bury field dropdowns
     * - Schema explorer tab
     */
    List<SearchField> getFields(SearchEngineConfig config);

    /**
     * Get sample values for a specific field.
     * Used in NexaRank admin UI for rule value dropdowns.
     * e.g. getFieldValues("brand", config) → ["Duracell", "Bosch", "Mobil 1"]
     */
    List<String> getFieldValues(String fieldName, SearchEngineConfig config);

    /**
     * Translate NexaRank rules into engine-specific DSL.
     * Returns a map that can be injected directly into the search query.
     *
     * For Elasticsearch: returns FunctionScore/Pinned query JSON
     * For Solr: returns boost query parameters and elevated documents
     *
     * @throws com.nexarank.api.exception.RuleTranslationException if the
     *         rules cannot be translated — never lets a raw/implementation-
     *         specific exception escape instead.
     */
    EnrichedQuery translateRules(
        String query,
        List<MerchRule> rules,
        SearchEngineConfig config
    );

    /**
     * Engine type this adapter handles.
     */
    SearchEngineConfig.EngineType supportedEngine();
}
