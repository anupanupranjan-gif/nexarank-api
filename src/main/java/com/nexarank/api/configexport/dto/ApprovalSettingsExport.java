// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.configexport.dto;

/** Tenant-wide only — confirmed no per-project override exists on Project. */
public record ApprovalSettingsExport(ExportMeta meta, boolean autoPublishRules) {}
