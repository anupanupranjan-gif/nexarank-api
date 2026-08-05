// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.repository;

import com.nexarank.api.model.ZeroResultQuery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ZeroResultQueryRepository extends JpaRepository<ZeroResultQuery, String> {

    @Query("SELECT z.query, COUNT(z) as occurrences FROM ZeroResultQuery z " +
           "WHERE z.tenantId = :tenantId AND z.projectId = :projectId " +
           "AND z.occurredAt >= :since " +
           "GROUP BY z.query ORDER BY occurrences DESC")
    List<Object[]> findTopZeroResultQueries(@Param("tenantId") String tenantId,
                                             @Param("projectId") String projectId,
                                             @Param("since") Instant since);

    /** NR-36: findTopZeroResultQueries with an explicit upper bound for custom date ranges. */
    @Query("SELECT z.query, COUNT(z) as occurrences FROM ZeroResultQuery z " +
           "WHERE z.tenantId = :tenantId AND z.projectId = :projectId " +
           "AND z.occurredAt >= :start AND z.occurredAt < :end " +
           "GROUP BY z.query ORDER BY occurrences DESC")
    List<Object[]> findTopZeroResultQueriesBetween(@Param("tenantId") String tenantId,
                                                    @Param("projectId") String projectId,
                                                    @Param("start") Instant start,
                                                    @Param("end") Instant end);

    long countByTenantIdAndProjectIdAndOccurredAtAfter(
            String tenantId, String projectId, Instant since);

    long countByTenantIdAndProjectIdAndOccurredAtBetween(
            String tenantId, String projectId, Instant start, Instant end);

    /** NR-59: recovery-rate numerator for the Analytics overview KPI. */
    long countByTenantIdAndProjectIdAndOccurredAtBetweenAndRecoveredTrue(
            String tenantId, String projectId, Instant start, Instant end);

    /**
     * NR-59: most recent row in-window carrying a suggestion for this exact
     * query text, used to annotate the zero-result table with a
     * recovered/suggestedQuery badge per query without a second grouped query.
     */
    Optional<ZeroResultQuery> findFirstByTenantIdAndProjectIdAndQueryIgnoreCaseAndSuggestedQueryIsNotNullAndOccurredAtBetweenOrderByOccurredAtDesc(
            String tenantId, String projectId, String query, Instant start, Instant end);
}
