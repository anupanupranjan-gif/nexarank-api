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

    @Query(value = "SELECT c.query, COUNT(c.id) as clicks, COALESCE(s.impressions, 0) as impressions " +
           "FROM click_events c " +
           "LEFT JOIN (SELECT query, COUNT(*) as impressions FROM search_events " +
           "WHERE tenant_id = :tenantId AND project_id = :projectId AND searched_at >= :since " +
           "GROUP BY query) s ON c.query = s.query " +
           "WHERE c.tenant_id = :tenantId AND c.project_id = :projectId " +
           "AND c.clicked_at >= :since " +
           "GROUP BY c.query, s.impressions ORDER BY impressions DESC", nativeQuery = true)
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
    @Query("SELECT c.productId, COUNT(c) as clickCount " +
            "FROM ClickEvent c " +
            "WHERE c.tenantId = :tenantId AND c.projectId = :projectId " +
            "AND c.sessionId = :sessionId AND c.clickedAt >= :since " +
            "GROUP BY c.productId ORDER BY clickCount DESC")
    List<Object[]> findTopClickedProductsBySession(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("sessionId") String sessionId,
            @Param("since") Instant since);
}
