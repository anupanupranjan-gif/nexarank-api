// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.nexarank.api.model.Project;
import com.nexarank.api.model.User;
import com.nexarank.api.model.UserProject;
import com.nexarank.api.repository.ProjectRepository;
import com.nexarank.api.repository.UserProjectRepository;
import com.nexarank.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ProjectAccessService — the layer that decides which
 * project a user's token may be scoped to. Pure Mockito, no Spring context:
 * this service has only repository dependencies, so a full application
 * context buys nothing here.
 */
@ExtendWith(MockitoExtension.class)
class ProjectAccessServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserProjectRepository userProjectRepository;
    @Mock private ProjectRepository projectRepository;

    private ProjectAccessService service;

    private User tenantWideUser(User.Role role) {
        User u = new User();
        u.setId("user-1");
        u.setTenantId("tenant-a");
        u.setRole(role);
        return u;
    }

    private User projectScopedUser() {
        User u = new User();
        u.setId("user-2");
        u.setTenantId("tenant-a");
        u.setRole(User.Role.MERCHANDISER);
        return u;
    }

    private Project project(String id, String tenantId, boolean enabled) {
        Project p = new Project();
        p.setId(id);
        p.setTenantId(tenantId);
        p.setEnabled(enabled);
        return p;
    }

    void setUp() {
        service = new ProjectAccessService(userRepository, userProjectRepository, projectRepository);
    }

    // ── Tenant-wide roles (VIEWER/ADMIN/STAKEHOLDER/TENANT_ADMIN/SUPER_ADMIN) ──

    @Test
    void tenantWideRole_canValidateAccessToAnyEnabledProjectInOwnTenant() {
        setUp();
        User admin = tenantWideUser(User.Role.ADMIN);
        when(projectRepository.findByTenantIdAndId("tenant-a", "project-x"))
                .thenReturn(Optional.of(project("project-x", "tenant-a", true)));

        assertThat(service.validateAccess(admin, "project-x")).isTrue();
    }

    @Test
    void tenantWideRole_cannotValidateAccessToDisabledProject() {
        setUp();
        User admin = tenantWideUser(User.Role.ADMIN);
        when(projectRepository.findByTenantIdAndId("tenant-a", "project-x"))
                .thenReturn(Optional.of(project("project-x", "tenant-a", false)));

        assertThat(service.validateAccess(admin, "project-x")).isFalse();
    }

    @Test
    void tenantWideRole_cannotValidateAccessToAnotherTenantsProject_evenByGuessingId() {
        setUp();
        User admin = tenantWideUser(User.Role.ADMIN);
        // The project exists, but under a different tenant — findByTenantIdAndId
        // scopes the lookup itself, so a cross-tenant id must resolve empty,
        // not fall through to an unscoped findById.
        when(projectRepository.findByTenantIdAndId("tenant-a", "victim-project"))
                .thenReturn(Optional.empty());

        assertThat(service.validateAccess(admin, "victim-project")).isFalse();
    }

    // ── Project-scoped roles (MERCHANDISER/APPROVER) ──────────────────────────

    @Test
    void projectScopedRole_canValidateAccessOnlyToAssignedEnabledProject() {
        setUp();
        User merch = projectScopedUser();
        UserProject assignment = new UserProject();
        assignment.setUserId("user-2");
        assignment.setProjectId("project-x");
        assignment.setRole(User.Role.MERCHANDISER);

        when(userProjectRepository.findByUserIdAndProjectId("user-2", "project-x"))
                .thenReturn(List.of(assignment));
        when(projectRepository.findById("project-x"))
                .thenReturn(Optional.of(project("project-x", "tenant-a", true)));

        assertThat(service.validateAccess(merch, "project-x")).isTrue();
    }

    @Test
    void projectScopedRole_cannotValidateAccessToUnassignedProject_evenInOwnTenant() {
        setUp();
        User merch = projectScopedUser();
        // No user_projects row for this pair at all — the exact IDOR shape:
        // a project-scoped user simply naming another project id in their
        // own tenant that they were never assigned to.
        when(userProjectRepository.findByUserIdAndProjectId("user-2", "project-b"))
                .thenReturn(List.of());

        assertThat(service.validateAccess(merch, "project-b")).isFalse();
    }

    @Test
    void projectScopedRole_cannotActivateUnassignedProject_throws() {
        setUp();
        User merch = projectScopedUser();
        when(userProjectRepository.findByUserIdAndProjectId("user-2", "project-b"))
                .thenReturn(List.of());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.activateProject(merch, "project-b"));
    }

    // ── SUPER_ADMIN: enum exists, must not silently grant anything beyond ────
    // ── what ADMIN already gets at THIS layer (project activation) ───────────

    @Test
    void superAdmin_isTreatedIdenticallyToAdmin_forProjectActivation_noExtraPrivilege() {
        setUp();
        User superAdmin = tenantWideUser(User.Role.SUPER_ADMIN);
        when(projectRepository.findByTenantIdAndId("tenant-a", "project-x"))
                .thenReturn(Optional.of(project("project-x", "tenant-a", true)));

        // SUPER_ADMIN is bucketed into TENANT_WIDE_ROLES same as ADMIN — this
        // pins that current, documented behavior (activation only, not the
        // separate question of endpoint-level authorization, covered in
        // SecurityConfig tests) so a future change can't silently widen or
        // narrow it without a test noticing.
        assertThat(service.isTenantWide(superAdmin)).isTrue();
        assertThat(service.validateAccess(superAdmin, "project-x")).isTrue();
        assertThat(service.resolveRoles(superAdmin, "project-x")).containsExactly("SUPER_ADMIN");
    }

    // ── isProjectAdminFor: must require BOTH roles on THIS specific project ──

    @Test
    void isProjectAdminFor_requiresBothMerchandiserAndApprover_onTheSameProject() {
        setUp();
        User user = projectScopedUser();
        UserProject merchOnA = new UserProject();
        merchOnA.setUserId("user-2"); merchOnA.setProjectId("project-a"); merchOnA.setRole(User.Role.MERCHANDISER);

        when(userProjectRepository.findByUserIdAndProjectId("user-2", "project-a"))
                .thenReturn(List.of(merchOnA));

        // Only MERCHANDISER on project-a, no APPROVER — must not qualify as
        // PROJECT_ADMIN for project-a.
        assertThat(service.isProjectAdminFor(user, "project-a")).isFalse();
    }

    @Test
    void isProjectAdminFor_doesNotLeakAcrossProjects() {
        setUp();
        User user = projectScopedUser();
        // MERCHANDISER on project-a and APPROVER on project-b separately —
        // must NOT combine across projects into a PROJECT_ADMIN on either.
        when(userProjectRepository.findByUserIdAndProjectId("user-2", "project-a"))
                .thenReturn(List.of(roleOn("project-a", User.Role.MERCHANDISER)));

        assertThat(service.isProjectAdminFor(user, "project-a")).isFalse();
    }

    private UserProject roleOn(String projectId, User.Role role) {
        UserProject up = new UserProject();
        up.setUserId("user-2");
        up.setProjectId(projectId);
        up.setRole(role);
        return up;
    }
}
