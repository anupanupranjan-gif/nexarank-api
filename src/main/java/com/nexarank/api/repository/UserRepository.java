// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.repository;

import com.nexarank.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByUsername(String username);
    Optional<User> findByTenantIdAndUsername(String tenantId, String username);
    List<User> findByTenantId(String tenantId);
    boolean existsByUsername(String username);
    boolean existsByTenantIdAndUsername(String tenantId, String username);
}
