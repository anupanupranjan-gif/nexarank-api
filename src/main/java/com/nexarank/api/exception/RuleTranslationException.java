// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.exception;

/**
 * A SearchEnginePort adapter failed to translate NexaRank's rules into the
 * target engine's DSL (ElasticsearchAdapter/SolrAdapter.translateRules()).
 * Not in the original three named exceptions for this ticket — added because
 * translateRules() performs no network I/O in either adapter (it's a pure
 * in-memory transform of already-fetched MerchRule data), so a failure there
 * is a data/logic problem, not a connectivity one. Reusing
 * EngineConnectionException for it would mislabel every such failure as an
 * upstream engine outage when the engine was never contacted.
 */
public class RuleTranslationException extends RuntimeException {
    public RuleTranslationException(String message, Throwable cause) {
        super(message, cause);
    }
}
