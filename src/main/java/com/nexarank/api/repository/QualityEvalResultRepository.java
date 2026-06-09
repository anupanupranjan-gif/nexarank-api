// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.repository;

import com.nexarank.api.model.QualityEvalResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface QualityEvalResultRepository extends JpaRepository<QualityEvalResult, String> {
    List<QualityEvalResult> findByTenantIdAndProjectIdOrderByRunAtDesc(String tenantId, String projectId);
    Optional<QualityEvalResult> findFirstByTenantIdAndProjectIdOrderByRunAtDesc(String tenantId, String projectId);
}
