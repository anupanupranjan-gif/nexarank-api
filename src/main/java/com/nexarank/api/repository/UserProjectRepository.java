// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.repository;

import com.nexarank.api.model.User;
import com.nexarank.api.model.UserProject;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserProjectRepository extends JpaRepository<UserProject, String> {
    List<UserProject> findByUserId(String userId);
    List<UserProject> findByProjectId(String projectId);

    /**
     * NR-121: a user can now hold more than one role on the same project
     * (e.g. PROJECT_ADMIN = MERCHANDISER + APPROVER assigned together), so
     * this returns every role row for the pair rather than at most one.
     */
    List<UserProject> findByUserIdAndProjectId(String userId, String projectId);

    Optional<UserProject> findByUserIdAndProjectIdAndRole(String userId, String projectId, User.Role role);
}
