// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarank.api.adapter.LlmAdapterFactory;
import com.nexarank.api.model.*;
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
    private final WatchedQueryService watchedQueryService;
    private final BusinessSignalService businessSignalService;
    private final LiveQuerySynonymCandidateService liveQuerySynonymCandidateService;

    public AiRuleSuggestionService(ClickEventRepository clickEventRepository,
                                   ZeroResultQueryRepository zeroResultRepository,
                                   MerchRuleRepository merchRuleRepository,
                                   SuggestionConfigService suggestionConfigService,
                                   LlmConfigService llmConfigService,
                                   LlmAdapterFactory llmAdapterFactory, WatchedQueryService watchedQueryService,
                                   BusinessSignalService businessSignalService,
                                   LiveQuerySynonymCandidateService liveQuerySynonymCandidateService) {
        this.clickEventRepository   = clickEventRepository;
        this.zeroResultRepository   = zeroResultRepository;
        this.merchRuleRepository    = merchRuleRepository;
        this.suggestionConfigService = suggestionConfigService;
        this.llmConfigService       = llmConfigService;
        this.llmAdapterFactory      = llmAdapterFactory;

        this.watchedQueryService = watchedQueryService;
        this.businessSignalService = businessSignalService;
        this.liveQuerySynonymCandidateService = liveQuerySynonymCandidateService;
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

        Map<String, List<BusinessSignal>> signalsByProduct =
                businessSignalService.getActiveSignalsByProduct();

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
                    // Factor in business signals
                    List<BusinessSignal> signals = signalsByProduct.getOrDefault(productId, List.of());
                    List<String> signalReasons = signals.stream().map(this::signalReason).toList();
                    if (!signalReasons.isEmpty()) {
                        suggestion.put("businessSignals", signalReasons);
                        suggestion.put("signalDrivenType", signals.stream()
                            .anyMatch(s -> s.getSignalType() == BusinessSignal.SignalType.PROMOTED ||
                                          s.getSignalType() == BusinessSignal.SignalType.SEASONAL)
                            ? "BOOST" : "BURY");
                    }
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
                String synonymSuggestion = askLlmForSynonyms(query, " and got zero results");
                Map<String, Object> suggestion = new LinkedHashMap<>();
                suggestion.put("type", "SYNONYM");
                suggestion.put("query", query);
                suggestion.put("reason", "Zero results — customers are not finding anything");
                suggestion.put("aiSuggestion", synonymSuggestion);
                suggestion.put("source", "ZERO_RESULT");
                suggestions.add(suggestion);
            } catch (Exception e) {
                log.warn("LLM suggestion failed for query '{}': {}", query, e.getMessage());
            }
        }
        return suggestions;
    }

    /**
     * NR-57 — Suggest SYNONYM rules for live queries (not just zero-result
     * ones), sourced from LlmSynonymSuggestionStage's frequency-tracked
     * live_query_synonym_candidates. LLM calls only happen here, on-demand,
     * for the queries actually reviewed on this page — never inline on the
     * hot search path (see LlmSynonymSuggestionStage's own javadoc).
     */
    public List<Map<String, Object>> suggestSynonymsForLiveQueries() {
        String tenantId  = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();
        SuggestionConfig config = suggestionConfigService.getConfig();

        Set<String> existingSynonymRuleQueries = merchRuleRepository
                .findByTenantIdAndProjectId(tenantId, projectId)
                .stream()
                .filter(r -> r.getType() == MerchRule.RuleType.SYNONYM)
                .map(r -> r.getQuery() == null ? "" : r.getQuery().toLowerCase().trim())
                .collect(Collectors.toSet());

        // Already covered by suggestSynonymsForZeroResults() — avoid double-listing the same query.
        Instant since = Instant.now().minus(config.getLookbackDays(), ChronoUnit.DAYS);
        Set<String> zeroResultQueries = zeroResultRepository
                .findTopZeroResultQueries(tenantId, projectId, since)
                .stream()
                .map(row -> ((String) row[0]).toLowerCase().trim())
                .collect(Collectors.toSet());

        List<Map<String, Object>> candidates = liveQuerySynonymCandidateService
                .findTopCandidates(tenantId, projectId, config.getMaxSuggestions() * 3);

        List<Map<String, Object>> suggestions = new ArrayList<>();
        for (Map<String, Object> row : candidates) {
            String query = (String) row.get("query");
            long hitCount = ((Number) row.get("hit_count")).longValue();

            if (hitCount < config.getMinClicks()) continue;
            if (existingSynonymRuleQueries.contains(query)) continue;
            if (zeroResultQueries.contains(query)) continue;

            try {
                String synonymSuggestion = askLlmForSynonyms(query,
                        " and got results, but broader synonym coverage could improve recall");
                Map<String, Object> suggestion = new LinkedHashMap<>();
                suggestion.put("type", "SYNONYM");
                suggestion.put("query", query);
                suggestion.put("reason", String.format(
                        "Searched %d time(s) with existing results — synonyms could broaden recall further", hitCount));
                suggestion.put("aiSuggestion", synonymSuggestion);
                suggestion.put("source", "LIVE_QUERY");
                suggestions.add(suggestion);
            } catch (Exception e) {
                log.warn("LLM live-query suggestion failed for query '{}': {}", query, e.getMessage());
            }
            if (suggestions.size() >= config.getMaxSuggestions()) break;
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

    private String signalReason(com.nexarank.api.model.BusinessSignal sig) {
        return switch (sig.getSignalType()) {
            case PROMOTED     -> "Promoted product — priority boost";
            case OUT_OF_STOCK -> "Out of stock — consider burying";
            case MARGIN_LOW   -> "Low margin — consider burying";
            case SEASONAL     -> "Seasonal signal — " + sig.getValue();
        };
    }

        private String askLlmForSynonyms(String query, String promptContext) {
        LlmConfig llmConfig = llmConfigService.getConfig().orElse(null);

        if (llmConfig == null) {
            log.debug("No LLM config found, using fallback synonym suggestion for '{}'", query);
            return fallbackSynonym(query);
        }

        try {
            return callLlmForSynonyms(query, promptContext, llmConfig);
        } catch (Exception e) {
            log.warn("LLM synonym suggestion failed for '{}': {}", query, e.getMessage());
            return fallbackSynonym(query);
        }
    }

    /**
     * @param promptContext short clause describing why synonyms are wanted — e.g.
     *                       " and got zero results" (NR-69) or " and got results, but
     *                       broader synonym coverage could improve recall" (NR-57) —
     *                       so the same LLM-calling machinery serves both suggestion sources.
     */
    private String callLlmForSynonyms(String query, String promptContext, LlmConfig llmConfig) {
        String prompt = String.format(
            "A customer searched for '%s' on an eCommerce site%s. " +
            "Suggest 2-3 alternative search terms or synonyms. " +
            "Reply with ONLY the synonyms separated by commas. No explanation.",
            query, promptContext
        );
        return llmAdapterFactory.getAdapter(llmConfig).rewrite(query, prompt + "\n%s\nSynonyms:", llmConfig);
    }

    private String fallbackSynonym(String query) {
        return query + " alternative, " + query + " replacement";
    }

    /**
     * Suggest a rule type + trigger content for a single zero-result query, for the
     * "Create Rule" action on the zero-result query list (NR-69). Defaults to SYNONYM
     * with a real LLM-generated suggestion when an LLM is configured and reachable;
     * falls back to BOOST (no meaningful synonym signal for a query with zero matches
     * to reorder anyway) when no LLM is configured or the call fails, letting the
     * merchandiser configure it manually.
     */
    public Map<String, Object> suggestRuleTypeForQuery(String query) {
        Map<String, Object> result = new LinkedHashMap<>();
        LlmConfig llmConfig = llmConfigService.getConfig().orElse(null);
        if (llmConfig == null) {
            result.put("type", "BOOST");
            result.put("aiSuggestion", null);
            return result;
        }
        try {
            result.put("type", "SYNONYM");
            result.put("aiSuggestion", callLlmForSynonyms(query, " and got zero results", llmConfig));
        } catch (Exception e) {
            log.warn("LLM rule-type suggestion failed for '{}': {} — defaulting to BOOST", query, e.getMessage());
            result.put("type", "BOOST");
            result.put("aiSuggestion", null);
        }
        return result;
    }
    /**
     * Check watched queries against actual performance and return alerts.
     */
    public List<Map<String, Object>> getWatchedQueryAlerts() {
        String tenantId  = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();
        SuggestionConfig config = suggestionConfigService.getConfig();
        Instant since = Instant.now().minus(config.getLookbackDays(), ChronoUnit.DAYS);

        List<WatchedQuery> watched = watchedQueryService.getEnabled();
        if (watched.isEmpty()) return List.of();

        List<Object[]> queryStats = clickEventRepository.findQueryStats(tenantId, projectId, since);

        // Build a map of query -> stats for fast lookup
        Map<String, Object[]> statsMap = new java.util.HashMap<>();
        for (Object[] row : queryStats) {
            statsMap.put(((String) row[0]).toLowerCase(), row);
        }

        List<Map<String, Object>> alerts = new java.util.ArrayList<>();
        for (WatchedQuery wq : watched) {
            Object[] stats = statsMap.get(wq.getQuery().toLowerCase());
            Map<String, Object> alert = new java.util.LinkedHashMap<>();
            alert.put("query", wq.getQuery());
            alert.put("notes", wq.getNotes());
            alert.put("watchedQueryId", wq.getId());

            if (stats == null) {
                alert.put("status", "NO_DATA");
                alert.put("message", "No click data found for this query in the last "
                        + config.getLookbackDays() + " days");
                alerts.add(alert);
                continue;
            }

            long clicks      = ((Number) stats[1]).longValue();
            long impressions = ((Number) stats[2]).longValue();
            double ctr       = impressions > 0 ? (double) clicks / impressions : 0.0;

            alert.put("clicks", clicks);
            alert.put("impressions", impressions);
            alert.put("ctr", Math.round(ctr * 1000.0) / 1000.0);

            List<String> breaches = new java.util.ArrayList<>();
            if (wq.getExpectedMinCtr() != null && ctr < wq.getExpectedMinCtr()) {
                breaches.add(String.format("CTR %.1f%% is below expected %.1f%%",
                        ctr * 100, wq.getExpectedMinCtr() * 100));
            }

            if (breaches.isEmpty()) {
                alert.put("status", "OK");
                alert.put("message", "Performance within expected thresholds");
            } else {
                alert.put("status", "BREACH");
                alert.put("message", String.join("; ", breaches));
            }
            alerts.add(alert);
        }
        return alerts;
    }
        /**
         * Suggest rules purely from business signals — no click data needed.
         */
        public List<Map<String, Object>> suggestSignalDrivenRules() {
            Map<String, List<BusinessSignal>> signalsByProduct =
                    businessSignalService.getActiveSignalsByProduct();

            List<Map<String, Object>> suggestions = new ArrayList<>();
            for (Map.Entry<String, List<BusinessSignal>> entry : signalsByProduct.entrySet()) {
                String productId = entry.getKey();
                for (BusinessSignal signal : entry.getValue()) {
                    Map<String, Object> suggestion = new LinkedHashMap<>();
                    suggestion.put("productId", productId);
                    suggestion.put("signalType", signal.getSignalType().name());
                    suggestion.put("value", signal.getValue());
                    suggestion.put("source", signal.getSource());
                    switch (signal.getSignalType()) {
                        case PROMOTED, SEASONAL -> {
                            suggestion.put("type", "BOOST");
                            suggestion.put("reason", signal.getSignalType() == BusinessSignal.SignalType.PROMOTED
                                    ? "Brand agreement — promote this product"
                                    : "Seasonal signal — " + signal.getValue());
                        }
                        case OUT_OF_STOCK -> {
                            suggestion.put("type", "BURY");
                            suggestion.put("reason", "Out of stock — hide from results");
                        }
                        case MARGIN_LOW -> {
                            suggestion.put("type", "BURY");
                            suggestion.put("reason", "Low margin — deprioritize: " + signal.getValue());
                        }
                    }
                    suggestions.add(suggestion);
                }
            }
            return suggestions;
        }
}
