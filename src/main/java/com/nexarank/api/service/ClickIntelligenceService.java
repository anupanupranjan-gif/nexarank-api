// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.nexarank.api.model.ClickAggregate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ClickIntelligenceService {

    private static final Logger log = LoggerFactory.getLogger(ClickIntelligenceService.class);

    // Thresholds
    private static final int MIN_IMPRESSIONS        = 10;
    private static final double LOW_CTR_THRESHOLD   = 0.05;  // below 5% CTR
    private static final double HIGH_CTR_THRESHOLD  = 0.20;  // above 20% CTR
    private static final double LOW_POSITION_FLOOR  = 4.0;   // avg position below 4

    private final ElasticsearchOperations esOps;

    public ClickIntelligenceService(ElasticsearchOperations esOps) {
        this.esOps = esOps;
    }

    /**
     * Low-CTR queries: many impressions but few clicks.
     * These need attention — likely poor results.
     */
    public List<ClickAggregate> getLowCtrQueries(int limit) {
        try {
            CriteriaQuery query = new CriteriaQuery(
                new Criteria("impressionCount").greaterThanEqual(MIN_IMPRESSIONS)
                    .and(new Criteria("ctr").lessThanEqual(LOW_CTR_THRESHOLD))
            );
            query.setMaxResults(limit);
            SearchHits<ClickAggregate> hits = esOps.search(query, ClickAggregate.class);
            return hits.getSearchHits().stream()
                .map(h -> h.getContent())
                .sorted((a, b) -> Double.compare(a.getCtr(), b.getCtr()))
                .toList();
        } catch (Exception e) {
            log.error("Failed to get low CTR queries", e);
            return List.of();
        }
    }

    /**
     * High-CTR products at low positions.
     * Users are scrolling past poor results to click these.
     * Candidates for BOOST rules.
     */
    public List<ClickAggregate> getBoostCandidates(int limit) {
        try {
            CriteriaQuery query = new CriteriaQuery(
                new Criteria("ctr").greaterThanEqual(HIGH_CTR_THRESHOLD)
                    .and(new Criteria("avgPosition").greaterThanEqual(LOW_POSITION_FLOOR))
            );
            query.setMaxResults(limit);
            SearchHits<ClickAggregate> hits = esOps.search(query, ClickAggregate.class);
            return hits.getSearchHits().stream()
                .map(h -> h.getContent())
                .sorted((a, b) -> Double.compare(b.getCtr(), a.getCtr()))
                .toList();
        } catch (Exception e) {
            log.error("Failed to get boost candidates", e);
            return List.of();
        }
    }

    /**
     * Top clicked products for a given query.
     * Used to populate PIN rule suggestions.
     */
    public List<ClickAggregate> getTopClickedForQuery(String query, int limit) {
        try {
            CriteriaQuery cq = new CriteriaQuery(
                new Criteria("query").is(query.toLowerCase())
            );
            cq.setMaxResults(limit);
            SearchHits<ClickAggregate> hits = esOps.search(cq, ClickAggregate.class);
            return hits.getSearchHits().stream()
                .map(h -> h.getContent())
                .sorted((a, b) -> Long.compare(b.getClickCount(), a.getClickCount()))
                .toList();
        } catch (Exception e) {
            log.error("Failed to get top clicked for query={}", query, e);
            return List.of();
        }
    }

    /**
     * Summary stats for the Click Intelligence dashboard.
     */
    public Map<String, Object> getSummaryStats() {
        List<ClickAggregate> lowCtr = getLowCtrQueries(100);
        List<ClickAggregate> boostCandidates = getBoostCandidates(100);

        return Map.of(
            "lowCtrQueryCount",      lowCtr.size(),
            "boostCandidateCount",   boostCandidates.size(),
            "thresholds", Map.of(
                "minImpressions",    MIN_IMPRESSIONS,
                "lowCtrThreshold",   LOW_CTR_THRESHOLD,
                "highCtrThreshold",  HIGH_CTR_THRESHOLD,
                "lowPositionFloor",  LOW_POSITION_FLOOR
            )
        );
    }
}
