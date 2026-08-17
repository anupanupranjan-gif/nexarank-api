// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.repository;

import com.nexarank.api.model.ApiAccessEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * NR-70 Tier 2. No update path is exposed anywhere — the only non-append
 * operation is purgeOlderThan, called solely by the scheduled retention job.
 */
public interface ApiAccessEventRepository extends JpaRepository<ApiAccessEvent, String> {

    Page<ApiAccessEvent> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    Page<ApiAccessEvent> findByTenantIdAndEventTypeOrderByCreatedAtDesc(
            String tenantId, ApiAccessEvent.EventType eventType, Pageable pageable);

    /**
     * eventType is bound as its String name rather than the enum: a null enum
     * bind is inferred as bytea by Postgres and breaks the IS NULL check, the
     * same reason the string filters below are explicitly cast.
     */
    @Query("SELECT e FROM ApiAccessEvent e WHERE e.tenantId = :tenantId "
         + "AND (CAST(:eventType AS String) IS NULL OR CAST(e.eventType AS String) = CAST(:eventType AS String)) "
         + "AND (CAST(:username AS String) IS NULL OR LOWER(e.username) LIKE LOWER(CONCAT('%', CAST(:username AS String), '%'))) "
         + "AND e.createdAt >= :start AND e.createdAt < :end "
         + "ORDER BY e.createdAt DESC")
    List<ApiAccessEvent> search(@Param("tenantId") String tenantId,
                                @Param("eventType") String eventType,
                                @Param("username") String username,
                                @Param("start") Instant start,
                                @Param("end") Instant end);

    @Modifying
    @Query("DELETE FROM ApiAccessEvent e WHERE e.tenantId = :tenantId AND e.createdAt < :cutoff")
    int purgeOlderThan(@Param("tenantId") String tenantId, @Param("cutoff") Instant cutoff);
}
