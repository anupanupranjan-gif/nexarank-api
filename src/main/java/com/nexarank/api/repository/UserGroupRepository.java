// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.repository;

import com.nexarank.api.model.UserGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserGroupRepository extends JpaRepository<UserGroup, String> {
    List<UserGroup> findByTenantId(String tenantId);
    Optional<UserGroup> findByTenantIdAndName(String tenantId, String name);
    List<UserGroup> findByTenantIdAndIsDefault(String tenantId, boolean isDefault);
}
