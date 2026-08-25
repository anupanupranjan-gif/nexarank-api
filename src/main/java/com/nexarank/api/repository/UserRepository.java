// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.repository;

import com.nexarank.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByUsername(String username);
    Optional<User> findByTenantIdAndUsername(String tenantId, String username);
    /** NR-65: forgot-password/resend-verification accept either username or email; email has no uniqueness constraint, so this takes the first match. */
    Optional<User> findFirstByEmail(String email);
    List<User> findByTenantId(String tenantId);
    List<User> findByTenantIdAndEmailIsNotNull(String tenantId);
    List<User> findByTenantIdAndRoleAndEmailIsNotNull(String tenantId, User.Role role);
    boolean existsByUsername(String username);
    boolean existsByTenantIdAndUsername(String tenantId, String username);
    List<User> findByLastActiveProjectId(String projectId);

    /**
     * NR-121: project-scoped equivalent of findByTenantIdAndRoleAndEmailIsNotNull,
     * for the project-scoped roles (MERCHANDISER/APPROVER) whose access to a rule
     * is governed by user_projects rather than a tenant-wide User.role match.
     */
    @Query("SELECT DISTINCT u FROM User u JOIN UserProject up ON up.userId = u.id " +
           "WHERE u.tenantId = :tenantId AND up.projectId = :projectId AND up.role = :role AND u.email IS NOT NULL")
    List<User> findByTenantIdAndProjectIdAndRoleAndEmailIsNotNull(@Param("tenantId") String tenantId,
                                                                   @Param("projectId") String projectId,
                                                                   @Param("role") User.Role role);
}
