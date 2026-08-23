// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.compliance;

/** NR-155: the kinds of regulated/sensitive data a {@link Pii}-tagged field can hold. */
public enum PiiCategory {
    /** Names, on their own or alongside other fields, that identify a specific person. */
    DIRECT_IDENTIFIER,
    /** Email, phone, or other channels that reach a specific person. */
    CONTACT_INFO,
    /** Passwords, tokens, and anything else that grants access. Never stored in plaintext. */
    CREDENTIAL,
    /** IP addresses — regulated as personal data under GDPR even without a name attached. */
    IP_ADDRESS,
    /** Session/device identifiers that don't name a person but can be correlated back to one. */
    PSEUDONYMOUS_ID,
    /** Free-text or structured fields that may incidentally carry PII depending on what a user typed. */
    OTHER_SENSITIVE
}
