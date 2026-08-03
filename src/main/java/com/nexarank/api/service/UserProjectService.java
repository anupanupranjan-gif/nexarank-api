// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.nexarank.api.model.Project;
import com.nexarank.api.model.User;
import com.nexarank.api.model.UserProject;
import com.nexarank.api.repository.ProjectRepository;
import com.nexarank.api.repository.UserProjectRepository;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * NR-121 step 2: write path for user_projects, the future source of truth
 * for project-scoped roles. Nothing here is read by JwtAuthFilter,
 * SecurityConfig, or any authorization check yet — that wiring is step 3,
 * intentionally blocked on NR-120 (session management) landing first. This
 * service only makes it possible to create the correct rows.
 *
 * ADMIN/VIEWER/STAKEHOLDER (and the deprecated TENANT_ADMIN/SUPER_ADMIN)
 * stay on User.role and are rejected here — user_projects is authoritative
 * only for MERCHANDISER, APPROVER, and the composite PROJECT_ADMIN concept
 * (which per NR-121's own definition is not a fourth stored role value, but
 * a user holding both MERCHANDISER and APPROVER rows on the same project).
 */
@Service
public class UserProjectService {

    private static final Set<User.Role> PROJECT_SCOPED_ROLES = EnumSet.of(User.Role.MERCHANDISER, User.Role.APPROVER);

    private final UserProjectRepository userProjectRepository;
    private final ProjectRepository projectRepository;

    public UserProjectService(UserProjectRepository userProjectRepository, ProjectRepository projectRepository) {
        this.userProjectRepository = userProjectRepository;
        this.projectRepository = projectRepository;
    }

    /** Grants a single project-scoped role. Idempotent — a no-op if the exact (user, project, role) row already exists. */
    public UserProject assignRole(String userId, String projectId, User.Role role) {
        if (!PROJECT_SCOPED_ROLES.contains(role)) {
            throw new IllegalArgumentException(
                    "user_projects only accepts MERCHANDISER or APPROVER; " + role + " stays on User.role");
        }
        return userProjectRepository.findByUserIdAndProjectIdAndRole(userId, projectId, role)
                .orElseGet(() -> {
                    UserProject up = new UserProject();
                    up.setId(UUID.randomUUID().toString());
                    up.setUserId(userId);
                    up.setProjectId(projectId);
                    up.setRole(role);
                    return userProjectRepository.save(up);
                });
    }

    /** PROJECT_ADMIN per NR-121's own definition: MERCHANDISER + APPROVER assigned together for one project, not a distinct stored role. */
    public List<UserProject> assignProjectAdmin(String userId, String projectId) {
        return List.of(
                assignRole(userId, projectId, User.Role.MERCHANDISER),
                assignRole(userId, projectId, User.Role.APPROVER)
        );
    }

    public void removeRole(String userId, String projectId, User.Role role) {
        userProjectRepository.findByUserIdAndProjectIdAndRole(userId, projectId, role)
                .ifPresent(userProjectRepository::delete);
    }

    public List<UserProject> getProjectRoles(String userId) {
        return userProjectRepository.findByUserId(userId);
    }

    public List<UserProject> getRolesForProject(String userId, String projectId) {
        return userProjectRepository.findByUserIdAndProjectId(userId, projectId);
    }

    /** NR-121 step 7: the inverse lookup — every role row for a project, backing "who's on this project" UI. */
    public List<UserProject> getRolesInProject(String projectId) {
        return userProjectRepository.findByProjectId(projectId);
    }

    /**
     * Grants a project-scoped role on every project in a tenant — the same
     * "don't tighten access" bridge philosophy as the V37 migration's
     * backfill, applied to newly-created users so they behave identically
     * to pre-migration users rather than needing special-case logic.
     * Narrowing a user to specific projects remains a manual follow-up
     * action via assignRole/removeRole, not something this does.
     */
    public List<UserProject> grantRoleOnAllTenantProjects(String userId, String tenantId, User.Role role) {
        List<Project> projects = projectRepository.findByTenantId(tenantId);
        return projects.stream().map(p -> assignRole(userId, p.getId(), role)).toList();
    }
}
