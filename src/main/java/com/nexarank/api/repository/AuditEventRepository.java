// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.repository;

import com.nexarank.api.model.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

/**
 * NR-70: append-only. No update path is exposed anywhere in the application;
 * the only non-append operation is purgeOlderThan, called solely by the
 * scheduled retention job (AuditRetentionService).
 */
public interface AuditEventRepository extends JpaRepository<AuditEvent, String> {
    Page<AuditEvent> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);
    Page<AuditEvent> findByTenantIdAndProjectIdOrderByCreatedAtDesc(String tenantId, String projectId, Pageable pageable);

    /**
     * NR-70 Tier 1: rule-change history restricted to the projects the caller
     * can actually see. Project scoping is applied in the query itself rather
     * than filtered after the fact, so paging counts stay correct.
     */
    @Query("SELECT e FROM AuditEvent e WHERE e.tenantId = :tenantId AND e.tier = 1 "
         + "AND e.projectId IN :projectIds "
         + "AND (CAST(:actor AS String) IS NULL OR LOWER(e.username) LIKE LOWER(CONCAT('%', CAST(:actor AS String), '%'))) "
         + "AND (CAST(:action AS String) IS NULL OR e.action = CAST(:action AS String)) "
         + "AND e.createdAt >= :start AND e.createdAt < :end "
         + "ORDER BY e.createdAt DESC")
    Page<AuditEvent> findTier1(@Param("tenantId") String tenantId,
                               @Param("projectIds") List<String> projectIds,
                               @Param("actor") String actor,
                               @Param("action") String action,
                               @Param("start") Instant start,
                               @Param("end") Instant end,
                               Pageable pageable);

    @Query("SELECT e FROM AuditEvent e WHERE e.tenantId = :tenantId AND e.tier = 1 "
         + "AND e.projectId IN :projectIds "
         + "AND (CAST(:actor AS String) IS NULL OR LOWER(e.username) LIKE LOWER(CONCAT('%', CAST(:actor AS String), '%'))) "
         + "AND (CAST(:action AS String) IS NULL OR e.action = CAST(:action AS String)) "
         + "AND e.createdAt >= :start AND e.createdAt < :end "
         + "ORDER BY e.createdAt DESC")
    List<AuditEvent> findTier1ForExport(@Param("tenantId") String tenantId,
                                        @Param("projectIds") List<String> projectIds,
                                        @Param("actor") String actor,
                                        @Param("action") String action,
                                        @Param("start") Instant start,
                                        @Param("end") Instant end);

    /** NR-70 Tier 2: tenant-wide, every tier, ADMIN only. */
    @Query("SELECT e FROM AuditEvent e WHERE e.tenantId = :tenantId "
         + "AND (CAST(:actor AS String) IS NULL OR LOWER(e.username) LIKE LOWER(CONCAT('%', CAST(:actor AS String), '%'))) "
         + "AND (CAST(:action AS String) IS NULL OR e.action = CAST(:action AS String)) "
         + "AND e.createdAt >= :start AND e.createdAt < :end "
         + "ORDER BY e.createdAt DESC")
    Page<AuditEvent> findTier2(@Param("tenantId") String tenantId,
                               @Param("actor") String actor,
                               @Param("action") String action,
                               @Param("start") Instant start,
                               @Param("end") Instant end,
                               Pageable pageable);

    @Query("SELECT e FROM AuditEvent e WHERE e.tenantId = :tenantId "
         + "AND (CAST(:actor AS String) IS NULL OR LOWER(e.username) LIKE LOWER(CONCAT('%', CAST(:actor AS String), '%'))) "
         + "AND (CAST(:action AS String) IS NULL OR e.action = CAST(:action AS String)) "
         + "AND e.createdAt >= :start AND e.createdAt < :end "
         + "ORDER BY e.createdAt DESC")
    List<AuditEvent> findTier2ForExport(@Param("tenantId") String tenantId,
                                        @Param("actor") String actor,
                                        @Param("action") String action,
                                        @Param("start") Instant start,
                                        @Param("end") Instant end);

    /** Distinct action names present for a tenant — powers the UI's filter dropdown. */
    @Query("SELECT DISTINCT e.action FROM AuditEvent e WHERE e.tenantId = :tenantId "
         + "AND (CAST(:tier AS Integer) IS NULL OR e.tier = CAST(:tier AS Integer)) ORDER BY e.action")
    List<String> findDistinctActions(@Param("tenantId") String tenantId, @Param("tier") Integer tier);

    @Modifying
    @Query("DELETE FROM AuditEvent e WHERE e.tenantId = :tenantId AND e.createdAt < :cutoff")
    int purgeOlderThan(@Param("tenantId") String tenantId, @Param("cutoff") Instant cutoff);
}
