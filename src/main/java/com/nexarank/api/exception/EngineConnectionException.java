// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.exception;

/**
 * The configured search engine (Elasticsearch/Solr) could not be reached or
 * used — no adapter registered for the configured engine type
 * (SearchEngineAdapterFactory), or a genuine I/O failure talking to it. Never
 * thrown for a data/logic problem in NexaRank's own rule data — see
 * RuleTranslationException for that.
 */
public class EngineConnectionException extends RuntimeException {
    public EngineConnectionException(String message) {
        super(message);
    }

    public EngineConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
