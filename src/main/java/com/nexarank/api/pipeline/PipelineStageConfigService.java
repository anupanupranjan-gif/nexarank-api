// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.pipeline;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the real, per-tenant/project enabled + order overrides from
 * pipeline_stage_config (V18) — previously written by the Pipeline Editor UI
 * (NR-39) but never actually read by QueryPipelineOrchestrator, so toggling a
 * stage off in that UI had zero effect on live serving. This is the fix.
 *
 * A stage with no DB row for a given tenant/project (e.g. a brand-new stage,
 * or a project created before this service existed) defaults to enabled=true
 * at its own defaultOrder() — identical to the old always-on behavior — so
 * this is purely additive: it can only narrow/reorder what already ran.
 *
 * Cached 30s per tenant/project (same pattern as TenantCorsConfig) since this
 * is consulted on every single enrich() call and the underlying config only
 * changes when an admin edits it in the Pipeline Editor — not worth a DB
 * round trip against the <20ms p99 enrich target on every request.
 */
@Service
public class PipelineStageConfigService {

    private static final long REFRESH_INTERVAL_MS = 30_000;

    private final JdbcTemplate jdbc;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public PipelineStageConfigService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Filters out disabled stages and reorders by the DB's stage_order, falling back to defaultOrder(). */
    public List<PipelineStage> applyOverrides(List<PipelineStage> defaultStages, String tenantId, String projectId) {
        Map<String, StageOverride> overrides = overridesFor(tenantId, projectId);
        return defaultStages.stream()
                .filter(s -> overrides.getOrDefault(s.name(), StageOverride.DEFAULT_ENABLED).enabled())
                .sorted(Comparator.comparingInt(s ->
                        overrides.containsKey(s.name())
                                ? overrides.get(s.name()).order()
                                : s.defaultOrder()))
                .toList();
    }

    /**
     * NR-59: standalone enabled check for a stage name not registered as a real
     * PipelineStage bean (ZERO_RESULT_RECOVERY runs outside the orchestrator's
     * own loop — see LlmZeroResultRecoveryService). Same 30s cache as applyOverrides.
     */
    public boolean isEnabled(String stageName, String tenantId, String projectId) {
        return overridesFor(tenantId, projectId)
                .getOrDefault(stageName, StageOverride.DEFAULT_ENABLED)
                .enabled();
    }

    private Map<String, StageOverride> overridesFor(String tenantId, String projectId) {
        String key = tenantId + "::" + projectId;
        CacheEntry entry = cache.get(key);
        long now = System.currentTimeMillis();
        if (entry == null || now - entry.fetchedAt > REFRESH_INTERVAL_MS) {
            entry = refresh(tenantId, projectId);
            cache.put(key, entry);
        }
        return entry.overrides;
    }

    private CacheEntry refresh(String tenantId, String projectId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT stage_name, enabled, stage_order
            FROM pipeline_stage_config
            WHERE tenant_id = ? AND project_id = ?
            """, tenantId, projectId);

        Map<String, StageOverride> map = new HashMap<>();
        for (Map<String, Object> row : rows) {
            map.put((String) row.get("stage_name"),
                    new StageOverride((Boolean) row.get("enabled"), ((Number) row.get("stage_order")).intValue()));
        }
        return new CacheEntry(map, System.currentTimeMillis());
    }

    private record StageOverride(boolean enabled, int order) {
        static final StageOverride DEFAULT_ENABLED = new StageOverride(true, 0);
    }

    private record CacheEntry(Map<String, StageOverride> overrides, long fetchedAt) {}
}
