// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.pipeline;

import com.nexarank.api.model.EnrichedQuery;
import com.nexarank.api.model.MerchRule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Carries query state through every pipeline stage.
 *
 * Pre-query stages read/write: currentQuery, queryClass
 * Rule application stage writes: matchedRules, enrichedQuery, abTestId, abVariant
 * Post-query stages (26d) will read/write: results
 *
 * The orchestrator reads enrichedQuery at the end and returns it to
 * RuleEnrichmentService, which returns it to the caller unchanged.
 * No changes to EnrichedQuery shape — search-api stays untouched.
 */
public class PipelineContext {

    // ── Identity ──────────────────────────────────────────────────────────────

    private final String tenantId;
    private final String projectId;
    private final String sessionId;
    private final String engineType;
    private final String zone;
    private final Map<String, String> selectedFacets;

    // ── Query state (mutated by pre-query stages) ─────────────────────────────

    private final String originalQuery;
    private String currentQuery;     // updated by spell correction, LLM rewrite, etc.
    private String queryClass;       // set by QueryClassification stage (26b)

    // ── Rule application results ──────────────────────────────────────────────

    private List<MerchRule> matchedRules = new ArrayList<>();
    private String abTestId;
    private String abVariant;

    // ── Final output — written by RuleApplicationStage ────────────────────────

    private EnrichedQuery enrichedQuery;

    // ── Observability — each stage appends one entry ──────────────────────────

    private final List<StageTrace> traces = new ArrayList<>();

    // ── Arbitrary inter-stage metadata ────────────────────────────────────────

    private final Map<String, Object> metadata = new HashMap<>();

    // ── Constructor ───────────────────────────────────────────────────────────

    public PipelineContext(String tenantId, String projectId, String sessionId,
                           String query, String engineType, String zone,
                           Map<String, String> selectedFacets) {
        this.tenantId       = tenantId;
        this.projectId      = projectId;
        this.sessionId      = sessionId;
        this.originalQuery  = query;
        this.currentQuery   = query;
        this.engineType     = engineType;
        this.zone           = zone;
        this.selectedFacets = selectedFacets != null ? selectedFacets : Map.of();
    }

    // ── Stage tracing ─────────────────────────────────────────────────────────

    public void addTrace(String stageName, String inputSummary,
                         String outputSummary, long durationMs, boolean skipped) {
        traces.add(new StageTrace(stageName, inputSummary, outputSummary, durationMs, skipped));
    }

    public record StageTrace(
        String stageName,
        String inputSummary,
        String outputSummary,
        long durationMs,
        boolean skipped
    ) {}

    // ── Helpers ───────────────────────────────────────────────────────────────

    public boolean isMatchAll() {
        return originalQuery == null
            || originalQuery.isBlank()
            || originalQuery.trim().equals("*");
    }

    public boolean queryWasRewritten() {
        return currentQuery != null && !currentQuery.equals(originalQuery);
    }

    // ── Getters / setters ─────────────────────────────────────────────────────

    public String getTenantId()                          { return tenantId; }
    public String getProjectId()                         { return projectId; }
    public String getSessionId()                         { return sessionId; }
    public String getEngineType()                        { return engineType; }
    public String getZone()                              { return zone; }
    public Map<String, String> getSelectedFacets()       { return selectedFacets; }
    public String getOriginalQuery()                     { return originalQuery; }

    public String getCurrentQuery()                      { return currentQuery; }
    public void setCurrentQuery(String q)                { this.currentQuery = q; }

    public String getQueryClass()                        { return queryClass; }
    public void setQueryClass(String qc)                 { this.queryClass = qc; }

    public List<MerchRule> getMatchedRules()             { return matchedRules; }
    public void setMatchedRules(List<MerchRule> rules)   { this.matchedRules = rules; }

    public String getAbTestId()                          { return abTestId; }
    public void setAbTestId(String id)                   { this.abTestId = id; }

    public String getAbVariant()                         { return abVariant; }
    public void setAbVariant(String v)                   { this.abVariant = v; }

    public EnrichedQuery getEnrichedQuery()              { return enrichedQuery; }
    public void setEnrichedQuery(EnrichedQuery eq)       { this.enrichedQuery = eq; }

    public List<StageTrace> getTraces()                  { return List.copyOf(traces); }

    public Map<String, Object> getMetadata()             { return metadata; }
    public void putMetadata(String key, Object val)      { metadata.put(key, val); }
    public Object getMetadata(String key)                { return metadata.get(key); }
}
