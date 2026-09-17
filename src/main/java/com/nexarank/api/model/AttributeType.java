// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.model;

/**
 * Canonical, engine-agnostic attribute type, shared by every SearchEnginePort
 * adapter (ElasticsearchAdapter, SolrAdapter) so both populate SearchField's
 * type information from the same vocabulary instead of each emitting its own
 * ad hoc strings.
 *
 * UNKNOWN is a required, explicit fallback for any native engine type this
 * enum doesn't recognize — never silently coerce an unrecognized type to
 * KEYWORD or any other member.
 *
 * MULTI_VALUE exists for a future field-cardinality signal but nothing maps
 * to it yet: neither Elasticsearch (arrays are transparent on any type) nor
 * Solr (multiValued is a separate boolean, not a distinct schema type)
 * encodes "multi-valued" as a native type the way they encode keyword/int/etc.
 */
public enum AttributeType {
    TEXT,
    KEYWORD,
    INTEGER,
    LONG,
    FLOAT,
    DOUBLE,
    BOOLEAN,
    DATE,
    GEO,
    MULTI_VALUE,
    UNKNOWN
}
