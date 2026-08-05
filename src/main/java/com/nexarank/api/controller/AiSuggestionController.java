// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.service.AiRuleSuggestionService;
import com.nexarank.api.service.LlmZeroResultRecoveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/suggestions")
public class AiSuggestionController {

    private final AiRuleSuggestionService suggestionService;
    private final LlmZeroResultRecoveryService zeroResultRecoveryService;

    public AiSuggestionController(AiRuleSuggestionService suggestionService,
                                   LlmZeroResultRecoveryService zeroResultRecoveryService) {
        this.suggestionService = suggestionService;
        this.zeroResultRecoveryService = zeroResultRecoveryService;
    }

    @GetMapping("/boost")
    public ResponseEntity<?> getBoostSuggestions() {
        return ResponseEntity.ok(suggestionService.suggestBoostRules());
    }

    @GetMapping("/synonyms")
    public ResponseEntity<?> getSynonymSuggestions() {
        return ResponseEntity.ok(suggestionService.suggestSynonymsForZeroResults());
    }

    /** NR-57 — synonym suggestions sourced from live (not just zero-result) queries. */
    @GetMapping("/synonyms/live")
    public ResponseEntity<?> getLiveQuerySynonymSuggestions() {
        return ResponseEntity.ok(suggestionService.suggestSynonymsForLiveQueries());
    }

    @GetMapping("/rule-type-for-query")
    public ResponseEntity<?> getRuleTypeForQuery(@RequestParam String query) {
        return ResponseEntity.ok(suggestionService.suggestRuleTypeForQuery(query));
    }

    @PostMapping("/apply")
    public ResponseEntity<?> applySuggestion(@RequestBody Map<String, Object> suggestion) {
        return ResponseEntity.ok(suggestionService.createSuggestedRule(suggestion));
    }

    @GetMapping("/alerts")
    public ResponseEntity<?> getAlerts() {
        return ResponseEntity.ok(suggestionService.getWatchedQueryAlerts());
    }

    @GetMapping("/signals")
    public ResponseEntity<?> getSignalSuggestions() {
        return ResponseEntity.ok(suggestionService.suggestSignalDrivenRules());
    }

    /**
     * NR-59 — called by search-api synchronously, right after its own search
     * comes back with zero hits. Stateless: returns a suggestion (or null),
     * writes nothing. search-api owns the retry and logs the outcome back via
     * POST /zero-results — see LlmZeroResultRecoveryService's javadoc for why.
     */
    @PostMapping("/zero-result-recovery")
    public ResponseEntity<?> recoverZeroResultQuery(@RequestBody Map<String, Object> body) {
        String query = (String) body.get("query");
        String suggestedQuery = zeroResultRecoveryService.suggestAlternativeQuery(query);
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("suggestedQuery", suggestedQuery);
        return ResponseEntity.ok(result);
    }
}
