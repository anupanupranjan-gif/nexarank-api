// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.exception;

/**
 * A rule, A/B test, or rule-side of an A/B test could not be found by id.
 * Replaces the IllegalArgumentException("... not found: " + id) pattern
 * previously used for this in RuleAbTestService, which GlobalExceptionHandler
 * mapped to 400 Bad Request — a "this id doesn't exist" condition is a 404,
 * not a malformed-request condition.
 */
public class RuleNotFoundException extends RuntimeException {
    public RuleNotFoundException(String message) {
        super(message);
    }
}
