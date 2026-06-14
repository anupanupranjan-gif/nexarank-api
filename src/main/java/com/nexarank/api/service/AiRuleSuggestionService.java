// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarank.api.adapter.LlmAdapterFactory;
import com.nexarank.api.model.LlmConfig;
import com.nexarank.api.model.MerchRule;
import com.nexarank.api.model.SuggestionConfig;
import com.nexarank.api.repository.ClickEventRepository;
import com.nexarank.api.repository.MerchRuleRepository;
import com.nexarank.api.repository.ZeroResultQueryRepository;
import com.nexarank.api.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AiRuleSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(AiRuleSuggestionService.class);

    private final ClickEventRepository clickEventRepository;
    private final ZeroResultQueryRepository zeroResultRepository;
    private final MerchRuleRepository merchRuleRepository;
    private final SuggestionConfigService suggestionConfigService;
    private final LlmConfigService llmConfigService;
    private final LlmAdapterFactory llmAdapterFactory;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiRuleSuggestionService(ClickEventRepository clickEventRepository,
                                    ZeroResultQueryRepository zeroResultRepository,
                                    MerchRuleRepository merchRuleRepository,
                                    SuggestionConfigService suggestionConfigService,
                                    LlmConfigService llmConfigService,
                                    LlmAdapterFactory llmAdapterFactory) {
        this.clickEventRepository   = clickEventRepository;
        this.zeroResultRepository   = zeroResultRepository;
        this.merchRuleRepository    = merchRuleRepository;
        this.suggestionConfigService = suggestionConfigService;
        this.llmConfigService       = llmConfigService;
        this.llmAdapterFactory      = llmAdapterFactory;
    }

    /**
     * Suggest BOOST rules for products clicked at low positions.
     * Thresholds driven by SuggestionConfig (configurable per project).
     */
    public List<Map<String, Object>> suggestBoostRules() {
        String tenantId  = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();
        SuggestionConfig config = suggestionConfigService.getConfig();
        Instant since = Instant.now().minus(config.getLookbackDays(), ChronoUnit.DAYS);

        List<Object[]> productStats = clickEventRepository
                .findProductClickStats(tenantId, projectId, since);

        Set<String> existingRuleQueries = merchRuleRepository
                .findByTenantIdAndProjectId(tenantId, projectId)
                .stream()
                .filter(r -> r.getType() == MerchRule.RuleType.BOOST)
                .map(MerchRule::getQuery)
                .collect(Collectors.toSet());

        List<Map<String, Object>> suggestions = new ArrayList<>();

        productStats.stream()
                .filter(row -> {
                    double avgPos = ((Number) row[3]).doubleValue();
                    long clicks   = ((Number) row[4]).longValue();
                    return avgPos >= config.getMaxClickPosition()
                        && clicks >= config.getMinClicks();
                })
                .limit(config.getMaxSuggestions())
                .forEach(row -> {
                    String query       = (String) row[0];
                    String productId   = (String) row[1];
                    String productTitle = (String) row[2];
                    double avgPos      = ((Number) row[3]).doubleValue();
                    long clicks        = ((Number) row[4]).longValue();

                    Map<String, Object> suggestion = new LinkedHashMap<>();
                    suggestion.put("type", "BOOST");
                    suggestion.put("query", query);
                    suggestion.put("productId", productId);
                    suggestion.put("productTitle", productTitle);
                    suggestion.put("avgPosition", Math.round(avgPos * 10.0) / 10.0);
                    suggestion.put("clicks", clicks);
                    suggestion.put("reason", String.format(
                        "'%s' was clicked %d time(s) at avg position %.1f — boost it higher",
                        productTitle, clicks, avgPos));
                    suggestion.put("alreadyHasRule", existingRuleQueries.contains(query));
                    suggestions.add(suggestion);
                });

        return suggestions;
    }

    /**
     * Suggest SYNONYM rules for zero-result queries using configured LLM.
     */
    public List<Map<String, Object>> suggestSynonymsForZeroResults() {
        String tenantId  = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();
        SuggestionConfig config = suggestionConfigService.getConfig();
        Instant since = Instant.now().minus(config.getLookbackDays(), ChronoUnit.DAYS);

        List<Object[]> zeroResultQueries = zeroResultRepository
                .findTopZeroResultQueries(tenantId, projectId, since);

        List<String> meaningfulQueries = zeroResultQueries.stream()
                .map(row -> (String) row[0])
                .filter(q -> q.length() > 3 && !q.startsWith("zzz") && !q.startsWith("test"))
                .limit(5)
                .collect(Collectors.toList());

        if (meaningfulQueries.isEmpty()) return List.of();

        List<Map<String, Object>> suggestions = new ArrayList<>();
        for (String query : meaningfulQueries) {
            try {
                String synonymSuggestion = askLlmForSynonyms(query);
                Map<String, Object> suggestion = new LinkedHashMap<>();
                suggestion.put("type", "SYNONYM");
                suggestion.put("query", query);
                suggestion.put("reason", "Zero results — customers are not finding anything");
                suggestion.put("aiSuggestion", synonymSuggestion);
                suggestions.add(suggestion);
            } catch (Exception e) {
                log.warn("LLM suggestion failed for query '{}': {}", query, e.getMessage());
            }
        }
        return suggestions;
    }

    /**
     * Auto-create a suggested rule as PENDING_REVIEW.
     */
    public MerchRule createSuggestedRule(Map<String, Object> suggestion) {
        String tenantId  = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();
        String username  = SecurityContextHolder.getContext().getAuthentication().getName();

        MerchRule rule = new MerchRule();
        rule.setId(UUID.randomUUID().toString());
        rule.setTenantId(tenantId);
        rule.setProjectId(projectId);
        rule.setQuery((String) suggestion.get("query"));
        rule.setType(MerchRule.RuleType.valueOf((String) suggestion.get("type")));
        rule.setStatus(MerchRule.RuleStatus.PENDING_REVIEW);
        rule.setEnabled(false);
        rule.setSubmittedBy(username + " (AI)");
        rule.setCreatedAt(Instant.now());
        rule.setUpdatedAt(Instant.now());
        rule.setPriority(50);

        if ("BOOST".equals(suggestion.get("type"))) {
            rule.setBoostField("id");
            rule.setBoostValue((String) suggestion.get("productId"));
            rule.setBoostFactor(2.0f);
        } else if ("SYNONYM".equals(suggestion.get("type"))) {
            String aiSuggestion = (String) suggestion.get("aiSuggestion");
            rule.setSynonymsJson("[\"" + suggestion.get("query") + "\",\""
                + aiSuggestion.replace("\"", "'") + "\"]");
        }

        return merchRuleRepository.save(rule);
    }

    // ── LLM integration ───────────────────────────────────────────────────────

    /**
     * Ask the configured LLM for synonym suggestions.
     * Uses LlmConfigService + LlmAdapterFactory — same as LlmQueryRewriteStage.
     * Falls back to a simple static response if no LLM configured.
     *
     * Uses HttpURLConnection (not HttpClient) for Java 25 AArch64 compatibility.
     */
    private String askLlmForSynonyms(String query) {
        LlmConfig llmConfig = llmConfigService.getConfig().orElse(null);

        if (llmConfig == null) {
            log.debug("No LLM config found, using fallback synonym suggestion for '{}'", query);
            return fallbackSynonym(query);
        }

        String prompt = String.format(
            "A customer searched for '%s' on an eCommerce site and got zero results. " +
            "Suggest 2-3 alternative search terms or synonyms. " +
            "Reply with ONLY the synonyms separated by commas. No explanation.",
            query
        );

        try {
            return llmAdapterFactory.getAdapter(llmConfig).rewrite(query, prompt + "\n%s\nSynonyms:", llmConfig);
        } catch (Exception e) {
            log.warn("LLM synonym suggestion failed for '{}': {}", query, e.getMessage());
            return fallbackSynonym(query);
        }
    }

    private String fallbackSynonym(String query) {
        return query + " alternative, " + query + " replacement";
    }
}
