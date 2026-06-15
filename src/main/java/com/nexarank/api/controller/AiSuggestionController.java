// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.service.AiRuleSuggestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/suggestions")
public class AiSuggestionController {

    private final AiRuleSuggestionService suggestionService;

    public AiSuggestionController(AiRuleSuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    @GetMapping("/boost")
    public ResponseEntity<?> getBoostSuggestions() {
        return ResponseEntity.ok(suggestionService.suggestBoostRules());
    }

    @GetMapping("/synonyms")
    public ResponseEntity<?> getSynonymSuggestions() {
        return ResponseEntity.ok(suggestionService.suggestSynonymsForZeroResults());
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
}
