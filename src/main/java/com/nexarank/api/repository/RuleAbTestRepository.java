// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.repository;

import com.nexarank.api.model.RuleAbTest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RuleAbTestRepository extends JpaRepository<RuleAbTest, String> {

    List<RuleAbTest> findByTenantIdAndProjectId(String tenantId, String projectId);

    List<RuleAbTest> findByTenantIdAndProjectIdAndStatus(
            String tenantId, String projectId, RuleAbTest.TestStatus status);

    Optional<RuleAbTest> findByTenantIdAndProjectIdAndQueryAndStatus(
            String tenantId, String projectId, String query, RuleAbTest.TestStatus status);
}
