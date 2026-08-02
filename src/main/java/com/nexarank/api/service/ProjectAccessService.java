// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.nexarank.api.model.Project;
import com.nexarank.api.model.User;
import com.nexarank.api.model.UserProject;
import com.nexarank.api.repository.ProjectRepository;
import com.nexarank.api.repository.UserProjectRepository;
import com.nexarank.api.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * NR-121 step 3: resolves which project a user's access token should be
 * scoped to, and which role(s) they hold for it. Tenant-wide roles
 * (VIEWER/ADMIN/STAKEHOLDER/deprecated TENANT_ADMIN/SUPER_ADMIN) can
 * activate any project in their own tenant with an unrestricted switcher —
 * their ROLE doesn't change per project, only which project's data they're
 * currently looking at. Project-scoped roles (MERCHANDISER/APPROVER) are
 * gated by user_projects and can only activate projects they're assigned
 * to; a user holding both roles on the same project (PROJECT_ADMIN, not a
 * distinct stored role) gets both as simultaneous JWT authorities.
 *
 * True simultaneous cross-project aggregate views for tenant-wide roles
 * are explicitly out of scope here (NR-126) — this only ever resolves to
 * ONE active project per token, same mechanism for every role.
 */
@Service
public class ProjectAccessService {

    private static final Set<User.Role> TENANT_WIDE_ROLES = EnumSet.of(
            User.Role.VIEWER, User.Role.ADMIN, User.Role.STAKEHOLDER,
            User.Role.TENANT_ADMIN, User.Role.SUPER_ADMIN);

    private final UserRepository userRepository;
    private final UserProjectRepository userProjectRepository;
    private final ProjectRepository projectRepository;

    public ProjectAccessService(UserRepository userRepository,
                                 UserProjectRepository userProjectRepository,
                                 ProjectRepository projectRepository) {
        this.userRepository = userRepository;
        this.userProjectRepository = userProjectRepository;
        this.projectRepository = projectRepository;
    }

    private boolean isTenantWide(User user) {
        return TENANT_WIDE_ROLES.contains(user.getRole());
    }

    /** Every project a user may currently activate — their own assignments if project-scoped, every tenant project if tenant-wide. */
    public List<String> availableProjectIds(User user) {
        if (isTenantWide(user)) {
            return projectRepository.findByTenantId(user.getTenantId()).stream()
                    .map(Project::getId).toList();
        }
        return userProjectRepository.findByUserId(user.getId()).stream()
                .map(UserProject::getProjectId).distinct().toList();
    }

    public boolean validateAccess(User user, String projectId) {
        if (isTenantWide(user)) {
            return projectRepository.findByTenantIdAndId(user.getTenantId(), projectId).isPresent();
        }
        return !userProjectRepository.findByUserIdAndProjectId(user.getId(), projectId).isEmpty();
    }

    /**
     * PROJECT_ADMIN per NR-121's own definition: holds both MERCHANDISER and
     * APPROVER on this specific project. Used to authorize the user_projects
     * write path — a PROJECT_ADMIN may assign/remove MERCHANDISER/APPROVER
     * for a project they administer, but not for any other project, and
     * never user creation itself (that stays ADMIN-only, unchanged).
     */
    public boolean isProjectAdminFor(User user, String projectId) {
        Set<User.Role> roles = userProjectRepository.findByUserIdAndProjectId(user.getId(), projectId).stream()
                .map(UserProject::getRole).collect(Collectors.toSet());
        return roles.contains(User.Role.MERCHANDISER) && roles.contains(User.Role.APPROVER);
    }

    /** Effective role(s) for this user on this project. Caller must have already validated access. */
    public List<String> resolveRoles(User user, String projectId) {
        if (isTenantWide(user)) {
            return List.of(user.getRole().name());
        }
        return userProjectRepository.findByUserIdAndProjectId(user.getId(), projectId).stream()
                .map(up -> up.getRole().name()).distinct().toList();
    }

    /**
     * Which project to land a user in when no explicit projectId was
     * requested (login, or a plain /refresh). Prefers their stored
     * last_active_project_id if it's still valid; otherwise falls back to
     * a deterministic default (a project named "Main" for tenant-wide
     * roles, since there's no real chronological "first assigned" concept
     * for tenant-wide access; the lexicographically-first assigned project
     * for project-scoped roles, since user_projects has no creation
     * timestamp to order by).
     */
    public String resolveActiveProjectId(User user) {
        if (user.getLastActiveProjectId() != null && validateAccess(user, user.getLastActiveProjectId())) {
            return user.getLastActiveProjectId();
        }
        if (isTenantWide(user)) {
            List<Project> projects = projectRepository.findByTenantId(user.getTenantId());
            return projects.stream()
                    .filter(p -> "main".equalsIgnoreCase(p.getName()))
                    .map(Project::getId)
                    .findFirst()
                    .orElseGet(() -> projects.stream().map(Project::getId).min(Comparator.naturalOrder()).orElse(null));
        }
        return userProjectRepository.findByUserId(user.getId()).stream()
                .map(UserProject::getProjectId).distinct()
                .min(Comparator.naturalOrder()).orElse(null);
    }

    /** Validates, resolves roles, and persists the new last-active project in one step. */
    public List<String> activateProject(User user, String projectId) {
        if (!validateAccess(user, projectId)) {
            throw new IllegalArgumentException("User does not have access to project: " + projectId);
        }
        List<String> roles = resolveRoles(user, projectId);
        user.setLastActiveProjectId(projectId);
        userRepository.save(user);
        return roles;
    }
}
