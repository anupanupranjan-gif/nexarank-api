// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.repository;

import com.nexarank.api.model.FacetConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FacetConfigRepository extends JpaRepository<FacetConfig, String> {
    List<FacetConfig> findByTenantIdAndProjectIdOrderBySortOrderAsc(String tenantId, String projectId);
    List<FacetConfig> findByTenantIdAndProjectIdAndEnabledOrderBySortOrderAsc(String tenantId, String projectId, boolean enabled);
    List<FacetConfig> findByEnabledTrueOrderBySortOrderAsc();
    boolean existsByFieldName(String fieldName);
    List<FacetConfig> findAllByOrderBySortOrderAsc();
}
