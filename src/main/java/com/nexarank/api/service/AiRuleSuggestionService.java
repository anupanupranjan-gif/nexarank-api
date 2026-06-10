// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.nexarank.api.model.MerchRule;
import com.nexarank.api.repository.ClickEventRepository;
import com.nexarank.api.repository.MerchRuleRepository;
import com.nexarank.api.repository.ZeroResultQueryRepository;
import com.nexarank.api.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AiRuleSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(AiRuleSuggestionService.class);

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.model:gemma3:1b}")
    private String ollamaModel;

    private final ClickEventRepository clickEventRepository;
    private final ZeroResultQueryRepository zeroResultRepository;
    private final MerchRuleRepository merchRuleRepository;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public AiRuleSuggestionService(ClickEventRepository clickEventRepository,
                                    ZeroResultQueryRepository zeroResultRepository,
                                    MerchRuleRepository merchRuleRepository) {
        this.clickEventRepository = clickEventRepository;
        this.zeroResultRepository = zeroResultRepository;
        this.merchRuleRepository = merchRuleRepository;
    }

    /**
     * Analyze click patterns and suggest BOOST rules for products
     * clicked at low positions (high position number = low in results)
     */
    public List<Map<String, Object>> suggestBoostRules() {
        String tenantId = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);

        List<Object[]> productStats = clickEventRepository
                .findProductClickStats(tenantId, projectId, since);

        // Get existing rule queries to avoid duplicates
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
                    long clicks = ((Number) row[4]).longValue();
                    return avgPos >= 4.0 && clicks >= 1;
                })
                .limit(10)
                .forEach(row -> {
                    String query = (String) row[0];
                    String productId = (String) row[1];
                    String productTitle = (String) row[2];
                    double avgPos = ((Number) row[3]).doubleValue();
                    long clicks = ((Number) row[4]).longValue();

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
     * Use Ollama to suggest synonyms for zero-result queries
     */
    public List<Map<String, Object>> suggestSynonymsForZeroResults() {
        String tenantId = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);

        List<Object[]> zeroResultQueries = zeroResultRepository
                .findTopZeroResultQueries(tenantId, projectId, since);

        // Filter out obvious test queries
        List<String> meaningfulQueries = zeroResultQueries.stream()
                .map(row -> (String) row[0])
                .filter(q -> q.length() > 3 && !q.startsWith("zzz") && !q.startsWith("test"))
                .limit(5)
                .collect(Collectors.toList());

        if (meaningfulQueries.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> suggestions = new ArrayList<>();

        for (String query : meaningfulQueries) {
            try {
                String synonymSuggestion = askOllamaForSynonyms(query);
                Map<String, Object> suggestion = new LinkedHashMap<>();
                suggestion.put("type", "SYNONYM");
                suggestion.put("query", query);
                suggestion.put("reason", "Zero results — customers are not finding anything");
                suggestion.put("aiSuggestion", synonymSuggestion);
                suggestions.add(suggestion);
            } catch (Exception e) {
                log.warn("Ollama suggestion failed for query '{}': {}", query, e.getMessage());
            }
        }

        return suggestions;
    }

    /**
     * Auto-create a suggested rule as PENDING_REVIEW
     */
    public MerchRule createSuggestedRule(Map<String, Object> suggestion) {
        String tenantId = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

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
            rule.setSynonymsJson("[\"" + (String) suggestion.get("query") + "\",\"" +
                    aiSuggestion.replace("\"", "'") + "\"]");
        }

        return merchRuleRepository.save(rule);
    }

    private String askOllamaForSynonyms(String query) throws Exception {
        String prompt = String.format(
            "A customer searched for '%s' on an eCommerce site and got zero results. " +
            "Suggest 2-3 alternative search terms or synonyms they might use to find similar products. " +
            "Reply with ONLY the synonyms separated by commas, nothing else. No explanation.",
            query
        );

        String requestBody = String.format(
            "{\"model\":\"%s\",\"prompt\":\"%s\",\"stream\":false}",
            ollamaModel,
            prompt.replace("\"", "\\\"").replace("\n", " ")
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ollamaBaseUrl + "/api/generate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        // Parse response field from JSON
        String body = response.body();
        int responseIdx = body.indexOf("\"response\":\"");
        if (responseIdx == -1) return "";
        int start = responseIdx + 12;
        int end = body.indexOf("\"", start);
        return body.substring(start, end).replace("\\n", " ").trim();
    }
}
