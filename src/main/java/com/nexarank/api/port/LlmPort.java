// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.port;

import com.nexarank.api.model.LlmConfig;

/**
 * Engine-agnostic port for LLM interactions.
 *
 * Implementations: OllamaLlmAdapter, OpenAiLlmAdapter (future), etc.
 * Selected at runtime by LlmAdapterFactory based on LlmConfig.provider.
 *
 * To add a new provider: implement this interface, annotate @Component. Done.
 */
public interface LlmPort {

    /** Provider this adapter handles. */
    LlmConfig.LlmProvider supportedProvider();

    /**
     * Test connectivity to the configured LLM provider.
     * Returns true if reachable and responding.
     */
    boolean testConnection(LlmConfig config);

    /**
     * Rewrite/expand a search query using the configured LLM.
     * Returns the rewritten query, or the original if rewrite fails.
     * Must never throw — always returns a usable string.
     *
     * @param query          the current search query
     * @param promptTemplate the prompt template with %s placeholder for the query
     * @param config         LLM connection config
     */
    String rewrite(String query, String promptTemplate, LlmConfig config);
}
