// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.repository;

import com.nexarank.api.model.ZeroResultQuery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

public interface ZeroResultQueryRepository extends JpaRepository<ZeroResultQuery, String> {

    @Query("SELECT z.query, COUNT(z) as occurrences FROM ZeroResultQuery z " +
           "WHERE z.tenantId = :tenantId AND z.projectId = :projectId " +
           "AND z.occurredAt >= :since " +
           "GROUP BY z.query ORDER BY occurrences DESC")
    List<Object[]> findTopZeroResultQueries(@Param("tenantId") String tenantId,
                                             @Param("projectId") String projectId,
                                             @Param("since") Instant since);

    long countByTenantIdAndProjectIdAndOccurredAtAfter(
            String tenantId, String projectId, Instant since);
}
