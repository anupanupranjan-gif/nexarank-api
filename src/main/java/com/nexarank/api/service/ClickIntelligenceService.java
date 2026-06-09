// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.nexarank.api.model.ClickAggregate;
import com.nexarank.api.repository.ClickEventRepository;
import com.nexarank.api.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ClickIntelligenceService {

    private static final Logger log = LoggerFactory.getLogger(ClickIntelligenceService.class);
    private static final int MIN_IMPRESSIONS       = 5;
    private static final double LOW_CTR_THRESHOLD  = 0.05;
    private static final double HIGH_CTR_THRESHOLD = 0.20;
    private static final double LOW_POSITION_FLOOR = 4.0;

    private final ClickEventRepository clickEventRepository;

    public ClickIntelligenceService(ClickEventRepository clickEventRepository) {
        this.clickEventRepository = clickEventRepository;
    }

    public Map<String, Object> getSummaryStats() {
        String tenantId = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);

        List<Object[]> queryStats = clickEventRepository.findQueryStats(tenantId, projectId, since);

        long lowCtrCount = queryStats.stream()
                .filter(row -> {
                    long clicks = ((Number) row[1]).longValue();
                    long impressions = ((Number) row[2]).longValue();
                    return impressions >= MIN_IMPRESSIONS && impressions > 0
                            && (double) clicks / impressions < LOW_CTR_THRESHOLD;
                }).count();

        long boostCandidateCount = clickEventRepository.findProductClickStats(tenantId, projectId, since)
                .stream()
                .filter(row -> ((Number) row[3]).doubleValue() > LOW_POSITION_FLOOR)
                .count();

        Map<String, Object> summary = new HashMap<>();
        summary.put("lowCtrQueryCount", lowCtrCount);
        summary.put("boostCandidateCount", boostCandidateCount);
        summary.put("totalQueries", queryStats.size());
        summary.put("totalClicks", clickEventRepository.countByTenantIdAndProjectId(tenantId, projectId));
        summary.put("thresholds", Map.of(
                "minImpressions", MIN_IMPRESSIONS,
                "lowCtrThreshold", LOW_CTR_THRESHOLD,
                "highCtrThreshold", HIGH_CTR_THRESHOLD,
                "lowPositionFloor", LOW_POSITION_FLOOR
        ));
        return summary;
    }

    public List<ClickAggregate> getLowCtrQueries(int limit) {
        String tenantId = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);

        return clickEventRepository.findQueryStats(tenantId, projectId, since)
                .stream()
                .filter(row -> {
                    long clicks = ((Number) row[1]).longValue();
                    long impressions = ((Number) row[2]).longValue();
                    return impressions >= MIN_IMPRESSIONS && impressions > 0
                            && (double) clicks / impressions < LOW_CTR_THRESHOLD;
                })
                .limit(limit)
                .map(row -> {
                    ClickAggregate agg = new ClickAggregate();
                    agg.setQuery((String) row[0]);
                    agg.setClickCount(((Number) row[1]).longValue());
                    agg.setImpressionCount(((Number) row[2]).longValue());
                    agg.setCtr(agg.getImpressionCount() > 0
                            ? (double) agg.getClickCount() / agg.getImpressionCount() : 0.0);
                    return agg;
                })
                .collect(Collectors.toList());
    }

    public List<ClickAggregate> getBoostCandidates(int limit) {
        String tenantId = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);

        return clickEventRepository.findProductClickStats(tenantId, projectId, since)
                .stream()
                .filter(row -> ((Number) row[3]).doubleValue() > LOW_POSITION_FLOOR)
                .limit(limit)
                .map(row -> {
                    ClickAggregate agg = new ClickAggregate();
                    agg.setQuery((String) row[0]);
                    agg.setProductId((String) row[1]);
                    agg.setProductTitle((String) row[2]);
                    agg.setAvgPosition(((Number) row[3]).doubleValue());
                    agg.setClickCount(((Number) row[4]).longValue());
                    return agg;
                })
                .collect(Collectors.toList());
    }

    public List<ClickAggregate> getTopClickedForQuery(String query, int limit) {
        String tenantId = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);

        return clickEventRepository.findProductClickStats(tenantId, projectId, since)
                .stream()
                .filter(row -> query.equalsIgnoreCase((String) row[0]))
                .limit(limit)
                .map(row -> {
                    ClickAggregate agg = new ClickAggregate();
                    agg.setQuery((String) row[0]);
                    agg.setProductId((String) row[1]);
                    agg.setProductTitle((String) row[2]);
                    agg.setAvgPosition(((Number) row[3]).doubleValue());
                    agg.setClickCount(((Number) row[4]).longValue());
                    return agg;
                })
                .collect(Collectors.toList());
    }
}
