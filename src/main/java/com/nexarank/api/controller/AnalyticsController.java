// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.repository.ClickEventRepository;
import com.nexarank.api.repository.MerchRuleRepository;
import com.nexarank.api.repository.ZeroResultQueryRepository;
import com.nexarank.api.repository.SearchEventRepository;
import com.nexarank.api.repository.QualityEvalResultRepository;
import com.nexarank.api.security.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final ClickEventRepository clickEventRepository;
    private final MerchRuleRepository merchRuleRepository;
    private final ZeroResultQueryRepository zeroResultRepository;
    private final QualityEvalResultRepository qualityResultRepository;
    private final SearchEventRepository searchEventRepository;

    public AnalyticsController(ClickEventRepository clickEventRepository,
                                MerchRuleRepository merchRuleRepository,
                                ZeroResultQueryRepository zeroResultRepository,
                                QualityEvalResultRepository qualityResultRepository,
                                SearchEventRepository searchEventRepository) {
        this.clickEventRepository = clickEventRepository;
        this.merchRuleRepository = merchRuleRepository;
        this.zeroResultRepository = zeroResultRepository;
        this.qualityResultRepository = qualityResultRepository;
        this.searchEventRepository = searchEventRepository;
    }

    @GetMapping("/overview")
    public ResponseEntity<?> getOverview(@RequestParam(defaultValue = "30") int days) {
        String tenantId = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);

        List<Object[]> queryStats = clickEventRepository.findQueryStats(tenantId, projectId, since);
        long totalClicks = clickEventRepository.countByTenantIdAndProjectId(tenantId, projectId);

        var allRules = merchRuleRepository.findByTenantIdAndProjectId(tenantId, projectId);

        // Active rules count
        long activeRules = allRules.stream()
                .filter(r -> r.getStatus().name().equals("LIVE") && r.isEnabled()).count();

        long pendingRules = allRules.stream()
                .filter(r -> r.getStatus().name().equals("PENDING_REVIEW")).count();

        // Most relevant rule per zero-result query that was actioned via "Create Rule"
        // (NR-69) — prefer a LIVE rule over any other status if more than one rule was
        // ever linked to the same query text.
        Map<String, com.nexarank.api.model.MerchRule> ruleByZeroResultQuery = new java.util.HashMap<>();
        for (var rule : allRules) {
            String source = rule.getSourceZeroResultQuery();
            if (source == null) continue;
            String key = source.toLowerCase();
            var existing = ruleByZeroResultQuery.get(key);
            if (existing == null || (!existing.getStatus().name().equals("LIVE") && rule.getStatus().name().equals("LIVE"))) {
                ruleByZeroResultQuery.put(key, rule);
            }
        }

        // Top queries by click volume
        List<Map<String, Object>> topQueries = queryStats.stream()
                .limit(10)
                .map(row -> {
                    Map<String, Object> q = new LinkedHashMap<>();
                    q.put("query", row[0]);
                    q.put("clicks", ((Number) row[1]).longValue());
                    q.put("impressions", ((Number) row[2]).longValue());
                    long clicks = ((Number) row[1]).longValue();
                    long impressions = ((Number) row[2]).longValue();
                    q.put("ctr", impressions > 0 ? Math.round((double) clicks / impressions * 1000.0) / 1000.0 : 0.0);
                    return q;
                })
                .collect(Collectors.toList());

        // CTR distribution
        double avgCtr = queryStats.stream()
                .mapToDouble(row -> {
                    long clicks = ((Number) row[1]).longValue();
                    long impressions = ((Number) row[2]).longValue();
                    return impressions > 0 ? (double) clicks / impressions : 0.0;
                })
                .average().orElse(0.0);

        // Search volume stats
        long totalSearches = searchEventRepository.countByTenantIdAndProjectIdAndSearchedAtAfter(tenantId, projectId, since);
        Double avgLatencyMs = searchEventRepository.findAvgLatency(tenantId, projectId, since);
        long zeroResultsInPeriod = zeroResultRepository.countByTenantIdAndProjectIdAndOccurredAtAfter(tenantId, projectId, since);
        double zeroResultRate = totalSearches > 0
                ? Math.min(1.0, (double) zeroResultsInPeriod / totalSearches)
                : 0.0;

        // Zero result queries
        long zeroResultCount = zeroResultRepository.countByTenantIdAndProjectIdAndOccurredAtAfter(
                tenantId, projectId, since);
        List<Object[]> allZeroResultQueries = zeroResultRepository
                .findTopZeroResultQueries(tenantId, projectId, since);

        // actioned = a rule was created via "Create Rule" (NR-69) linking back to this
        // exact query text; resolved = that rule is LIVE+enabled and a search for the
        // same query has since returned results. Approximate signal (same spirit as the
        // query-level rule-performance attribution above) — a search event with the
        // same text could in principle predate the rule, but that's the accepted
        // tradeoff rather than building an exact time-ordered join for a dev/demo tool.
        long zeroResultActionedCount = 0, zeroResultUnactionedCount = 0;
        for (Object[] row : allZeroResultQueries) {
            String query = (String) row[0];
            if (ruleByZeroResultQuery.containsKey(query.toLowerCase())) zeroResultActionedCount++;
            else zeroResultUnactionedCount++;
        }

        List<Map<String, Object>> topZeroResults = allZeroResultQueries.stream().limit(10)
                .map(row -> {
                    String query = (String) row[0];
                    Map<String, Object> z = new LinkedHashMap<>();
                    z.put("query", query);
                    z.put("occurrences", ((Number) row[1]).longValue());
                    var rule = ruleByZeroResultQuery.get(query.toLowerCase());
                    boolean actioned = rule != null;
                    boolean resolved = actioned
                            && rule.getStatus().name().equals("LIVE") && rule.isEnabled()
                            && searchEventRepository.existsByTenantIdAndProjectIdAndQueryIgnoreCaseAndResultCountGreaterThanAndSearchedAtAfter(
                                    tenantId, projectId, query, 0, since);
                    z.put("actioned", actioned);
                    z.put("resolved", resolved);
                    if (rule != null) {
                        z.put("ruleId", rule.getId());
                        z.put("ruleType", rule.getType());
                        z.put("ruleStatus", rule.getStatus());
                    }
                    return z;
                })
                .collect(Collectors.toList());

        // Latest quality score
        var latestQuality = qualityResultRepository
                .findFirstByTenantIdAndProjectIdOrderByRunAtDesc(tenantId, projectId);

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("totalClicks", totalClicks);
        overview.put("totalQueries", queryStats.size());
        overview.put("avgCtr", Math.round(avgCtr * 1000.0) / 1000.0);
        overview.put("activeRules", activeRules);
        overview.put("pendingRules", pendingRules);
        overview.put("totalSearches", totalSearches);
        overview.put("zeroResultRate", Math.round(zeroResultRate * 1000.0) / 1000.0);
        overview.put("avgLatencyMs", avgLatencyMs != null ? Math.round(avgLatencyMs) : null);
        overview.put("zeroResultCount", zeroResultCount);
        overview.put("zeroResultActionedCount", zeroResultActionedCount);
        overview.put("zeroResultUnactionedCount", zeroResultUnactionedCount);
        overview.put("topZeroResultQueries", topZeroResults);
        overview.put("topQueries", topQueries);
        overview.put("latestNdcg10", latestQuality.map(q -> q.getNdcgAt10()).orElse(null));
        overview.put("latestMrr10", latestQuality.map(q -> q.getMrrAt10()).orElse(null));
        overview.put("latestQualityRunAt", latestQuality.map(q -> q.getRunAt()).orElse(null));
        overview.put("periodDays", days);

        return ResponseEntity.ok(overview);
    }


    @GetMapping("/trends")
    public ResponseEntity<?> getTrends(@RequestParam(defaultValue = "30") int days) {
        String tenantId = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);

        // Get daily search stats from search_events
        List<Map<String, Object>> trends = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            Instant dayStart = Instant.now().minus(i, ChronoUnit.DAYS)
                    .truncatedTo(ChronoUnit.DAYS);
            Instant dayEnd = dayStart.plus(1, ChronoUnit.DAYS);
            String dateStr = dayStart.toString().substring(0, 10);

            long searches = searchEventRepository
                    .countByTenantIdAndProjectIdAndSearchedAtBetween(tenantId, projectId, dayStart, dayEnd);
            long zeroResults = zeroResultRepository
                    .countByTenantIdAndProjectIdAndOccurredAtBetween(tenantId, projectId, dayStart, dayEnd);
            Double avgLatency = searchEventRepository
                    .findAvgLatencyBetween(tenantId, projectId, dayStart, dayEnd);

            if (searches > 0 || zeroResults > 0) {
                Map<String, Object> day = new LinkedHashMap<>();
                day.put("date", dateStr);
                day.put("searches", searches);
                day.put("zeroResults", zeroResults);
                day.put("zeroResultRate", searches > 0
                        ? Math.round((double) zeroResults / searches * 1000.0) / 1000.0 : 0.0);
                day.put("avgLatencyMs", avgLatency != null ? Math.round(avgLatency) : null);
                trends.add(day);
            }
        }

        return ResponseEntity.ok(trends);
    }

    @GetMapping("/rules-performance")
    public ResponseEntity<?> getRulesPerformance(@RequestParam(defaultValue = "30") int days) {
        String tenantId = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);

        var rules = merchRuleRepository.findByTenantIdAndProjectId(tenantId, projectId);

        // Query-level clicks/impressions (from search_events) for CTR + CTR lift.
        List<Object[]> queryStats = clickEventRepository.findQueryStats(tenantId, projectId, since);
        long totalClicks = 0, totalImpressions = 0;
        for (Object[] row : queryStats) {
            totalClicks += ((Number) row[1]).longValue();
            totalImpressions += ((Number) row[2]).longValue();
        }
        double avgCtr = totalImpressions > 0 ? (double) totalClicks / totalImpressions : 0.0;

        List<Object[]> revenueStats = clickEventRepository.findRevenueByQuery(tenantId, projectId, since);

        List<Map<String, Object>> performance = rules.stream().map(rule -> {
            // Approximate attribution: aggregate clicks/impressions/revenue across every
            // searched query that would have matched this rule's query text (whole-word,
            // case-insensitive — same semantics as MerchRuleService.containsRuleQuery).
            // There's no per-click rule attribution today, so this is a query-level proxy,
            // not an exact per-fire measurement.
            long ruleClicks = 0, ruleImpressions = 0;
            for (Object[] row : queryStats) {
                if (queryMatchesRule((String) row[0], rule.getQuery())) {
                    ruleClicks += ((Number) row[1]).longValue();
                    ruleImpressions += ((Number) row[2]).longValue();
                }
            }
            double revenue = 0;
            for (Object[] row : revenueStats) {
                if (queryMatchesRule((String) row[0], rule.getQuery())) {
                    revenue += ((Number) row[1]).doubleValue();
                }
            }
            double ctr = ruleImpressions > 0 ? (double) ruleClicks / ruleImpressions : 0.0;

            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", rule.getId());
            r.put("type", rule.getType());
            r.put("query", rule.getQuery());
            r.put("status", rule.getStatus());
            r.put("enabled", rule.isEnabled());
            r.put("createdAt", rule.getCreatedAt());
            r.put("submittedBy", rule.getSubmittedBy());
            r.put("approvedBy", rule.getApprovedBy());
            r.put("firedCount", rule.getFiredCount());
            r.put("lastFiredAt", rule.getLastFiredAt());
            r.put("neverFired", rule.getFiredCount() == 0);
            r.put("ctr", Math.round(ctr * 1000.0) / 1000.0);
            r.put("ctrLift", Math.round((ctr - avgCtr) * 1000.0) / 1000.0);
            r.put("revenueImpact", Math.round(revenue * 100.0) / 100.0);
            return r;
        }).collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("rules", performance);
        response.put("avgCtr", Math.round(avgCtr * 1000.0) / 1000.0);
        response.put("periodDays", days);

        return ResponseEntity.ok(response);
    }

    private boolean queryMatchesRule(String searchedQuery, String ruleQuery) {
        if (searchedQuery == null || ruleQuery == null) return false;
        if (searchedQuery.equalsIgnoreCase(ruleQuery)) return true;
        String pattern = "(?i)(^|\\s)" + java.util.regex.Pattern.quote(ruleQuery) + "(\\s|$)";
        return java.util.regex.Pattern.compile(pattern).matcher(searchedQuery).find();
    }
}
