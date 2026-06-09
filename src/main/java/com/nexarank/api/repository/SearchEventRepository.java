// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.repository;

import com.nexarank.api.model.SearchEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

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

    @Query("SELECT AVG(s.tookMs) FROM SearchEvent s WHERE s.tenantId = :tenantId " +
           "AND s.projectId = :projectId AND s.searchedAt >= :start AND s.searchedAt < :end " +
           "AND s.tookMs IS NOT NULL")
    Double findAvgLatencyBetween(@Param("tenantId") String tenantId,
                                  @Param("projectId") String projectId,
                                  @Param("start") Instant start,
                                  @Param("end") Instant end);
}
