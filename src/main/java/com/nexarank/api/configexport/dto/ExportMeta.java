// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.configexport.dto;

/**
 * NR-157/NR-164: every export category file carries this so a future format
 * change doesn't silently break older exports on import — NR-167's import
 * path should reject any schemaVersion it doesn't recognize rather than
 * best-effort parsing it.
 */
public record ExportMeta(int schemaVersion, String exportedAt, String tenantId, String projectId) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
}
