// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.repository.ClickEventRepository;
import com.nexarank.api.repository.MerchRuleRepository;
import com.nexarank.api.security.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final ClickEventRepository clickEventRepository;
    private final MerchRuleRepository merchRuleRepository;

    public AnalyticsController(ClickEventRepository clickEventRepository,
                                MerchRuleRepository merchRuleRepository) {
        this.clickEventRepository = clickEventRepository;
        this.merchRuleRepository = merchRuleRepository;
    }

    @GetMapping("/overview")
    public ResponseEntity<?> getOverview(@RequestParam(defaultValue = "30") int days) {
        String tenantId = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);

        List<Object[]> queryStats = clickEventRepository.findQueryStats(tenantId, projectId, since);
        long totalClicks = clickEventRepository.countByTenantIdAndProjectId(tenantId, projectId);

        // Active rules count
        long activeRules = merchRuleRepository.findByTenantIdAndProjectId(tenantId, projectId)
                .stream().filter(r -> r.getStatus().name().equals("APPROVED") && r.isEnabled()).count();

        long pendingRules = merchRuleRepository.findByTenantIdAndProjectId(tenantId, projectId)
                .stream().filter(r -> r.getStatus().name().equals("PENDING_REVIEW")).count();

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

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("totalClicks", totalClicks);
        overview.put("totalQueries", queryStats.size());
        overview.put("avgCtr", Math.round(avgCtr * 1000.0) / 1000.0);
        overview.put("activeRules", activeRules);
        overview.put("pendingRules", pendingRules);
        overview.put("topQueries", topQueries);
        overview.put("periodDays", days);

        return ResponseEntity.ok(overview);
    }

    @GetMapping("/rules-performance")
    public ResponseEntity<?> getRulesPerformance() {
        String tenantId = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();

        var rules = merchRuleRepository.findByTenantIdAndProjectId(tenantId, projectId);

        List<Map<String, Object>> performance = rules.stream().map(rule -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", rule.getId());
            r.put("type", rule.getType());
            r.put("query", rule.getQuery());
            r.put("status", rule.getStatus());
            r.put("enabled", rule.isEnabled());
            r.put("createdAt", rule.getCreatedAt());
            r.put("submittedBy", rule.getSubmittedBy());
            r.put("approvedBy", rule.getApprovedBy());
            return r;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(performance);
    }
}
