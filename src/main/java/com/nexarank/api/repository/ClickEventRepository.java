// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.repository;

import com.nexarank.api.model.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

public interface ClickEventRepository extends JpaRepository<ClickEvent, String> {

    List<ClickEvent> findByTenantIdAndProjectIdOrderByClickedAtDesc(
            String tenantId, String projectId);

    @Query("SELECT c.query, COUNT(c) as clicks, COUNT(DISTINCT c.sessionId) as impressions " +
           "FROM ClickEvent c WHERE c.tenantId = :tenantId AND c.projectId = :projectId " +
           "AND c.clickedAt >= :since " +
           "GROUP BY c.query ORDER BY impressions DESC")
    List<Object[]> findQueryStats(@Param("tenantId") String tenantId,
                                   @Param("projectId") String projectId,
                                   @Param("since") Instant since);

    @Query("SELECT c.query, c.productId, c.productTitle, AVG(c.position) as avgPosition, COUNT(c) as clicks " +
           "FROM ClickEvent c WHERE c.tenantId = :tenantId AND c.projectId = :projectId " +
           "AND c.clickedAt >= :since " +
           "GROUP BY c.query, c.productId, c.productTitle ORDER BY clicks DESC")
    List<Object[]> findProductClickStats(@Param("tenantId") String tenantId,
                                          @Param("projectId") String projectId,
                                          @Param("since") Instant since);

    long countByTenantIdAndProjectId(String tenantId, String projectId);
}
