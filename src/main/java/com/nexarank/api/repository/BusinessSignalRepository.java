// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.repository;

import com.nexarank.api.model.BusinessSignal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface BusinessSignalRepository extends JpaRepository<BusinessSignal, String> {

    List<BusinessSignal> findByTenantIdAndProjectId(String tenantId, String projectId);

    @Query("SELECT s FROM BusinessSignal s WHERE s.tenantId = :tenantId " +
           "AND s.projectId = :projectId " +
           "AND (s.validFrom IS NULL OR s.validFrom <= :now) " +
           "AND (s.validTo IS NULL OR s.validTo >= :now)")
    List<BusinessSignal> findActiveSignals(
        @Param("tenantId") String tenantId,
        @Param("projectId") String projectId,
        @Param("now") Instant now);

    @Query("SELECT s FROM BusinessSignal s WHERE s.tenantId = :tenantId " +
           "AND s.projectId = :projectId AND s.productId = :productId " +
           "AND (s.validFrom IS NULL OR s.validFrom <= :now) " +
           "AND (s.validTo IS NULL OR s.validTo >= :now)")
    List<BusinessSignal> findActiveSignalsForProduct(
        @Param("tenantId") String tenantId,
        @Param("projectId") String projectId,
        @Param("productId") String productId,
        @Param("now") Instant now);
}
