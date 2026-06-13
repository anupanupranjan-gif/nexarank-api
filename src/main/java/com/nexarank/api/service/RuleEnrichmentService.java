// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.nexarank.api.model.EnrichedQuery;
import com.nexarank.api.pipeline.QueryPipelineOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Phase 26a: delegates to QueryPipelineOrchestrator.
 * All rule lookup, A/B, and translation logic moved to RuleApplicationStage.
 * Public API (method signatures) unchanged — callers unaffected.
 */
@Service
public class RuleEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(RuleEnrichmentService.class);

    private final QueryPipelineOrchestrator orchestrator;

    public RuleEnrichmentService(QueryPipelineOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    public EnrichedQuery enrich(String query, String engineType, String zone) {
        return orchestrator.execute(query, engineType, zone, null, null);
    }

    public EnrichedQuery enrich(String query, String engineType, String zone, String sessionId) {
        return orchestrator.execute(query, engineType, zone, sessionId, null);
    }

    public EnrichedQuery enrich(String query, String engineType, String zone,
                                 String sessionId, Map<String, String> selectedFacets) {
        return orchestrator.execute(query, engineType, zone, sessionId, selectedFacets);
    }
}
