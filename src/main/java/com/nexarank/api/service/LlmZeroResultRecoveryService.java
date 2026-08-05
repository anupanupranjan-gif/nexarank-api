// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.nexarank.api.adapter.LlmAdapterFactory;
import com.nexarank.api.model.LlmConfig;
import com.nexarank.api.pipeline.PipelineStageConfigService;
import com.nexarank.api.port.LlmPort;
import com.nexarank.api.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * NR-59 — LLM Zero-Result Recovery.
 *
 * Stateless suggestion generator: given a query that returned zero results,
 * asks the configured LLM for one alternative query more likely to return
 * results. Deliberately does not execute or retry the search itself and does
 * not write to zero_result_queries — nexarank-api's pipeline never sees
 * search-api's actual result count (enrich() is called BEFORE the ES query
 * runs, see the migration comment on V47), so search-api owns the retry and
 * the analytics write, same separation of concerns as the rest of the
 * "instructions only" pipeline (ADR: NexaRank never touches search results).
 *
 * Reuses LlmPort.rewrite() rather than adding a fifth near-identical LlmPort
 * method — a reformulation suggestion is exactly what rewrite() already does,
 * just with a different prompt template (same reasoning NR-58 used to reuse
 * classify() for relevance grading instead of adding a new port method).
 */
@Service
public class LlmZeroResultRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(LlmZeroResultRecoveryService.class);

    // Exactly one %s — rewrite() does String.format(promptTemplate, query) internally.
    private static final String PROMPT_TEMPLATE =
            "A customer searched for '%s' on an eCommerce site and got ZERO results. " +
            "Suggest ONE alternative search query that is more likely to return results — " +
            "broaden an overly-specific term, fix a likely typo/misspelling, or use a more " +
            "common synonym. Reply with ONLY the alternative query text. No explanation, no quotes.";

    private final LlmConfigService llmConfigService;
    private final LlmAdapterFactory llmAdapterFactory;
    private final PipelineStageConfigService stageConfigService;

    public LlmZeroResultRecoveryService(LlmConfigService llmConfigService,
                                         LlmAdapterFactory llmAdapterFactory,
                                         PipelineStageConfigService stageConfigService) {
        this.llmConfigService = llmConfigService;
        this.llmAdapterFactory = llmAdapterFactory;
        this.stageConfigService = stageConfigService;
    }

    /**
     * Returns an alternative query, or null when: the stage is toggled off for
     * this tenant/project, no LLM is configured, the call fails, or the LLM's
     * response is blank/unusable/identical to the original (nothing worth
     * retrying with). Never throws.
     */
    public String suggestAlternativeQuery(String query) {
        String tenantId = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();

        if (!stageConfigService.isEnabled("ZERO_RESULT_RECOVERY", tenantId, projectId)) {
            return null;
        }
        if (query == null || query.isBlank()) {
            return null;
        }

        LlmConfig llmConfig = llmConfigService.getConfig().orElse(null);
        if (llmConfig == null) {
            log.debug("No LLM config found — skipping zero-result recovery for '{}'", query);
            return null;
        }

        try {
            LlmPort adapter = llmAdapterFactory.getAdapter(llmConfig);
            String suggestion = adapter.rewrite(query, PROMPT_TEMPLATE, llmConfig);
            if (suggestion == null) return null;

            String cleaned = suggestion.strip();
            if (cleaned.isEmpty() || cleaned.equalsIgnoreCase(query.strip())) {
                return null;
            }
            return cleaned;
        } catch (Exception e) {
            log.warn("LLM zero-result recovery failed for '{}': {}", query, e.getMessage());
            return null;
        }
    }
}
