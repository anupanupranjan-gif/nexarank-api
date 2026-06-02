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
