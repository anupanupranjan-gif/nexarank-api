// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.repository;

import com.nexarank.api.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, String> {
    List<Project> findByTenantIdAndEnabled(String tenantId, boolean enabled);
    Optional<Project> findByTenantIdAndId(String tenantId, String id);
}
