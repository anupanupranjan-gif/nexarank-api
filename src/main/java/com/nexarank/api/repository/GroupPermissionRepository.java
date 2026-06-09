// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.repository;

import com.nexarank.api.model.GroupPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GroupPermissionRepository extends JpaRepository<GroupPermission, String> {
    List<GroupPermission> findByGroupId(String groupId);
    void deleteByGroupId(String groupId);
}
