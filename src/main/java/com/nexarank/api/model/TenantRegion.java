// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.model;

/**
 * NR-155: the region a tenant's data is pinned to. Broad blocs, not
 * individual countries — this records intent/eligibility for the
 * application-layer foundations ticket, not a physical multi-region
 * deployment (separate infra work, out of scope here).
 */
public enum TenantRegion {
    US,
    EU,
    APAC
}
