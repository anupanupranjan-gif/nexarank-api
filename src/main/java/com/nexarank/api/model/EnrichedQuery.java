// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.model;

import java.util.List;
import java.util.Map;

/**
 * Engine-agnostic rule enrichment response.
 * Returned by POST /api/v1/rules/enrich.
 * Customer's search service applies this to their query.
 */
public class EnrichedQuery {

    private String originalQuery;
    private String expandedQuery;      // after SYNONYM expansion
    private String engineType;         // ELASTICSEARCH, SOLR

    private List<BoostInstruction> boosts;
    private List<PinInstruction> pins;
    private List<BuryInstruction> buries;
    private String redirectUrl;        // for REDIRECT rules
    private FacetInstructions facets;

    // Pre-translated engine-specific DSL (optional convenience)
    private Map<String, Object> engineDsl;

    private List<String> appliedRules; // rule IDs that fired
    private long processingMs;

    // ── Nested instruction types ──────────────────────────────────────────────

    public record BoostInstruction(
        String field,
        String value,
        float factor
    ) {}

    public record PinInstruction(
        String productId,
        int position
    ) {}

    public record BuryInstruction(
        String field,
        String value,
        float factor  // 0.0 - 0.5, lower = more buried
    ) {}

    public record FacetInstructions(
        List<String> visible,
        List<String> hidden,
        List<String> order
    ) {}

    // ── Getters and setters ───────────────────────────────────────────────────

    public String getOriginalQuery() { return originalQuery; }
    public void setOriginalQuery(String q) { this.originalQuery = q; }

    public String getExpandedQuery() { return expandedQuery; }
    public void setExpandedQuery(String q) { this.expandedQuery = q; }

    public String getEngineType() { return engineType; }
    public void setEngineType(String t) { this.engineType = t; }

    public List<BoostInstruction> getBoosts() { return boosts; }
    public void setBoosts(List<BoostInstruction> boosts) { this.boosts = boosts; }

    public List<PinInstruction> getPins() { return pins; }
    public void setPins(List<PinInstruction> pins) { this.pins = pins; }

    public List<BuryInstruction> getBuries() { return buries; }
    public void setBuries(List<BuryInstruction> buries) { this.buries = buries; }

    public String getRedirectUrl() { return redirectUrl; }
    public void setRedirectUrl(String url) { this.redirectUrl = url; }

    public FacetInstructions getFacets() { return facets; }
    public void setFacets(FacetInstructions facets) { this.facets = facets; }

    public Map<String, Object> getEngineDsl() { return engineDsl; }
    public void setEngineDsl(Map<String, Object> dsl) { this.engineDsl = dsl; }

    public List<String> getAppliedRules() { return appliedRules; }
    public void setAppliedRules(List<String> rules) { this.appliedRules = rules; }

    public long getProcessingMs() { return processingMs; }
    public void setProcessingMs(long ms) { this.processingMs = ms; }

    // A/B test context — present when a test is active for this query
    private String abTestId;
    private String abVariant;  // "A" or "B"

    public String getAbTestId() { return abTestId; }
    public void setAbTestId(String abTestId) { this.abTestId = abTestId; }
    public String getAbVariant() { return abVariant; }
    public void setAbVariant(String abVariant) { this.abVariant = abVariant; }
}
