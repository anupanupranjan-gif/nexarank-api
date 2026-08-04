// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Backs NR-57's LlmSynonymSuggestionStage. The pipeline stage only ever
 * upserts a frequency counter here (cheap, single indexed write, no LLM call)
 * — real LLM synonym generation happens later, on-demand, only for the
 * queries a merchandiser actually reviews (AiRuleSuggestionService), never
 * inline on the hot search path.
 */
@Service
public class LiveQuerySynonymCandidateService {

    private final JdbcTemplate jdbc;

    public LiveQuerySynonymCandidateService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Called from LlmSynonymSuggestionStage.execute() on every live query (when the stage is enabled). */
    public void track(String tenantId, String projectId, String query) {
        String normalized = query.toLowerCase().trim();
        jdbc.update("""
            INSERT INTO live_query_synonym_candidates
                (tenant_id, project_id, query, hit_count, first_seen_at, last_seen_at)
            VALUES (?, ?, ?, 1, NOW(), NOW())
            ON CONFLICT (tenant_id, project_id, query)
            DO UPDATE SET hit_count = live_query_synonym_candidates.hit_count + 1, last_seen_at = NOW()
            """, tenantId, projectId, normalized);
    }

    /** Top tracked queries by hit_count, for AiRuleSuggestionService.suggestSynonymsForLiveQueries(). */
    public List<Map<String, Object>> findTopCandidates(String tenantId, String projectId, int limit) {
        return jdbc.queryForList("""
            SELECT query, hit_count
            FROM live_query_synonym_candidates
            WHERE tenant_id = ? AND project_id = ?
            ORDER BY hit_count DESC
            LIMIT ?
            """, tenantId, projectId, limit);
    }
}
