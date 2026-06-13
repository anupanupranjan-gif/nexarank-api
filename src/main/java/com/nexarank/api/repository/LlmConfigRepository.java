// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.repository;

import com.nexarank.api.model.LlmConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LlmConfigRepository extends JpaRepository<LlmConfig, String> {

    Optional<LlmConfig> findFirstByTenantIdAndProjectId(String tenantId, String projectId);
}
