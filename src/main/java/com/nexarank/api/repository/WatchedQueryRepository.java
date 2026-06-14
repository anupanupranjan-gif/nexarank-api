// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.repository;

import com.nexarank.api.model.WatchedQuery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchedQueryRepository extends JpaRepository<WatchedQuery, String> {

    List<WatchedQuery> findByTenantIdAndProjectId(String tenantId, String projectId);

    List<WatchedQuery> findByTenantIdAndProjectIdAndEnabled(
        String tenantId, String projectId, boolean enabled);

    Optional<WatchedQuery> findByTenantIdAndProjectIdAndQuery(
        String tenantId, String projectId, String query);

    void deleteByTenantIdAndProjectIdAndId(String tenantId, String projectId, String id);
}
