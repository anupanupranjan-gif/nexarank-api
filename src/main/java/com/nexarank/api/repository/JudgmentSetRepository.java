// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.repository;

import com.nexarank.api.model.JudgmentSet;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface JudgmentSetRepository extends JpaRepository<JudgmentSet, String> {
    List<JudgmentSet> findByTenantIdAndProjectIdOrderByCreatedAtDesc(String tenantId, String projectId);
    Optional<JudgmentSet> findByTenantIdAndProjectIdAndName(String tenantId, String projectId, String name);
}
