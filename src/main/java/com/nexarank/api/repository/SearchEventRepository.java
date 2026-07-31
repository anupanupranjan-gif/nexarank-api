// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.repository;

import com.nexarank.api.model.SearchEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

public interface SearchEventRepository extends JpaRepository<SearchEvent, String> {

    long countByTenantIdAndProjectIdAndSearchedAtAfter(
            String tenantId, String projectId, Instant since);

    long countByTenantIdAndProjectIdAndResultCountAndSearchedAtAfter(
            String tenantId, String projectId, int resultCount, Instant since);

    @Query("SELECT AVG(s.tookMs) FROM SearchEvent s WHERE s.tenantId = :tenantId " +
           "AND s.projectId = :projectId AND s.searchedAt >= :since AND s.tookMs IS NOT NULL")
    Double findAvgLatency(@Param("tenantId") String tenantId,
                           @Param("projectId") String projectId,
                           @Param("since") Instant since);

    long countByTenantIdAndProjectIdAndSearchedAtBetween(
            String tenantId, String projectId, Instant start, Instant end);

    boolean existsByTenantIdAndProjectIdAndQueryIgnoreCaseAndResultCountGreaterThanAndSearchedAtAfter(
            String tenantId, String projectId, String query, int resultCount, Instant since);

    @Query("SELECT AVG(s.tookMs) FROM SearchEvent s WHERE s.tenantId = :tenantId " +
           "AND s.projectId = :projectId AND s.searchedAt >= :start AND s.searchedAt < :end " +
           "AND s.tookMs IS NOT NULL")
    Double findAvgLatencyBetween(@Param("tenantId") String tenantId,
                                  @Param("projectId") String projectId,
                                  @Param("start") Instant start,
                                  @Param("end") Instant end);

    /**
     * NR-36: p50/p95/p99 latency. No percentile function exists in JPQL, so
     * this is a native query — Postgres-specific (PERCENTILE_CONT).
     */
    @Query(value = "SELECT " +
            "PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY took_ms) AS p50, " +
            "PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY took_ms) AS p95, " +
            "PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY took_ms) AS p99, " +
            "AVG(took_ms) AS \"avgMs\", " +
            "COUNT(took_ms) AS \"sampleSize\" " +
            "FROM search_events " +
            "WHERE tenant_id = :tenantId AND project_id = :projectId " +
            "AND searched_at >= :since AND took_ms IS NOT NULL",
            nativeQuery = true)
    LatencyPercentiles findLatencyPercentiles(@Param("tenantId") String tenantId,
                                               @Param("projectId") String projectId,
                                               @Param("since") Instant since);

    /**
     * NR-36: search volume + zero-result count per project, across every
     * project in the tenant (unlike every other query here, deliberately
     * NOT scoped to a single projectId) — backs the "query volume by
     * project" search health report.
     */
    @Query(value = "SELECT project_id AS \"projectId\", COUNT(*) AS searches, " +
            "SUM(CASE WHEN result_count = 0 THEN 1 ELSE 0 END) AS \"zeroResults\" " +
            "FROM search_events " +
            "WHERE tenant_id = :tenantId AND searched_at >= :since " +
            "GROUP BY project_id",
            nativeQuery = true)
    List<ProjectVolumeRow> findVolumeByProject(@Param("tenantId") String tenantId,
                                                @Param("since") Instant since);

    interface LatencyPercentiles {
        Double getP50();
        Double getP95();
        Double getP99();
        Double getAvgMs();
        Long getSampleSize();
    }

    interface ProjectVolumeRow {
        String getProjectId();
        Long getSearches();
        Long getZeroResults();
    }
}
