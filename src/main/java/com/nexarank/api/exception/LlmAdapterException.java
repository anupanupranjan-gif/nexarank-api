// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.exception;

/**
 * The configured LLM provider has no registered adapter (LlmAdapterFactory),
 * or the LLM adapter could not be used in a context outside LlmPort.rewrite()/
 * classify()'s own "must never throw" contract — e.g. judgment auto-scoring
 * or AI rule suggestions selecting an adapter before ever calling it.
 */
public class LlmAdapterException extends RuntimeException {
    public LlmAdapterException(String message) {
        super(message);
    }

    public LlmAdapterException(String message, Throwable cause) {
        super(message, cause);
    }
}
