// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.repository;

import com.nexarank.api.model.SearchEngineConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SearchEngineConfigRepository extends JpaRepository<SearchEngineConfig, String> {
    Optional<SearchEngineConfig> findFirstByTenantIdAndProjectId(String tenantId, String projectId);
    Optional<SearchEngineConfig> findTopByOrderByCreatedAtDesc();
}
