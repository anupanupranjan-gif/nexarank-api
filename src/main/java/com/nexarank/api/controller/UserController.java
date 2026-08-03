// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.model.User;
import com.nexarank.api.model.UserGroupMembership;
import com.nexarank.api.model.UserProject;
import com.nexarank.api.repository.UserGroupMembershipRepository;
import com.nexarank.api.security.TenantContext;
import com.nexarank.api.service.ProjectAccessService;
import com.nexarank.api.service.UserProjectService;
import com.nexarank.api.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final UserProjectService userProjectService;
    private final ProjectAccessService projectAccessService;

    public UserController(UserService userService, UserProjectService userProjectService,
                           ProjectAccessService projectAccessService) {
        this.userService = userService;
        this.userProjectService = userProjectService;
        this.projectAccessService = projectAccessService;
    }

    /**
     * NR-121: ADMIN may assign/remove roles on any project; a PROJECT_ADMIN
     * (holds both MERCHANDISER + APPROVER on the target project) may only do
     * so for a project they themselves administer. SecurityConfig's matcher
     * only establishes coarse role presence — this is the real, per-project
     * check.
     */
    private boolean canManageProjectRoles(String projectId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User caller = userService.findByUsername(username).orElse(null);
        if (caller == null) return false;
        if (caller.getRole() == User.Role.ADMIN) return true;
        return projectAccessService.isProjectAdminFor(caller, projectId);
    }

    @GetMapping("/{id}/groups")
    public ResponseEntity<?> getUserGroups(@PathVariable String id) {
        return ResponseEntity.ok(userService.getUserGroups(id));
    }

    @PostMapping("/{id}/groups/{groupId}")
    public ResponseEntity<?> addUserToGroup(@PathVariable String id, @PathVariable String groupId) {
        return ResponseEntity.ok(userService.addUserToGroup(id, groupId));
    }

    @DeleteMapping("/{id}/groups/{groupId}")
    public ResponseEntity<?> removeUserFromGroup(@PathVariable String id, @PathVariable String groupId) {
        userService.removeUserFromGroup(id, groupId);
        return ResponseEntity.noContent().build();
    }

    // NR-121: write path for project-scoped roles (user_projects). Read
    // (GET) stays admin-only via the general /api/v1/users/** matcher,
    // unchanged; the write endpoints below additionally allow a
    // PROJECT_ADMIN caller, gated per-project by canManageProjectRoles().
    @GetMapping("/{id}/projects")
    public ResponseEntity<?> getUserProjectRoles(@PathVariable String id) {
        return ResponseEntity.ok(userProjectService.getProjectRoles(id));
    }

    @PostMapping("/{id}/projects/{projectId}")
    public ResponseEntity<?> assignUserProjectRole(@PathVariable String id, @PathVariable String projectId,
                                                    @RequestBody Map<String, String> body) {
        if (!canManageProjectRoles(projectId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You do not administer this project"));
        }
        User.Role role;
        try {
            role = User.Role.valueOf(body.getOrDefault("role", "").toUpperCase());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid role. Must be MERCHANDISER or APPROVER"));
        }
        try {
            return ResponseEntity.ok(userProjectService.assignRole(id, projectId, role));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/projects/{projectId}/project-admin")
    public ResponseEntity<?> assignUserProjectAdmin(@PathVariable String id, @PathVariable String projectId) {
        if (!canManageProjectRoles(projectId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You do not administer this project"));
        }
        return ResponseEntity.ok(userProjectService.assignProjectAdmin(id, projectId));
    }

    @DeleteMapping("/{id}/projects/{projectId}")
    public ResponseEntity<?> removeUserProjectRole(@PathVariable String id, @PathVariable String projectId,
                                                    @RequestParam String role) {
        if (!canManageProjectRoles(projectId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You do not administer this project"));
        }
        User.Role parsedRole;
        try {
            parsedRole = User.Role.valueOf(role.toUpperCase());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid role"));
        }
        userProjectService.removeRole(id, projectId, parsedRole);
        return ResponseEntity.noContent().build();
    }

    /**
     * NR-121 step 7: lightweight tenant user list backing project-role
     * assignment pickers. Deliberately NOT the full GET / (ADMIN-only,
     * returns the raw User entity) — this is a reduced field set available
     * to ADMIN and to any PROJECT_ADMIN of their own currently-active
     * project too, so they have someone to pick from when assigning roles
     * on the project they administer without exposing the full tenant user
     * roster/admin surface.
     */
    @GetMapping("/directory")
    public ResponseEntity<?> directory() {
        if (!canManageProjectRoles(TenantContext.getProjectId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Not authorized"));
        }
        // Only MERCHANDISER/APPROVER base-role users are meaningful picks here —
        // a project-scoped role attached to a tenant-wide user (VIEWER/ADMIN/
        // STAKEHOLDER) would be inert, since ProjectAccessService.isTenantWide
        // means their JWT roles never consult user_projects at all.
        List<Map<String, String>> result = userService.getAllUsers().stream()
                .filter(u -> u.getRole() == User.Role.MERCHANDISER || u.getRole() == User.Role.APPROVER)
                .map(u -> Map.of(
                        "id", u.getId(),
                        "username", u.getUsername(),
                        "email", u.getEmail() != null ? u.getEmail() : "",
                        "displayName", u.getDisplayName() != null ? u.getDisplayName() : "",
                        "role", u.getRole().name()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /**
     * NR-121 step 7: the inverse of GET /{id}/projects — every user with a
     * role on a given project, enriched with username/email for display.
     * Backs both the ADMIN per-project-roles editor and PROJECT_ADMIN's
     * own scoped "My Team" view.
     */
    @GetMapping("/project-roster/{projectId}")
    public ResponseEntity<?> projectRoster(@PathVariable String projectId) {
        if (!canManageProjectRoles(projectId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You do not administer this project"));
        }
        // NR-121: user_projects also carries the pre-existing V1 seed row for
        // ADMIN (harmless residue — ADMIN's real authority comes from
        // User.role, not this table, per ProjectAccessService.isTenantWide),
        // which would otherwise show up as noise in a roster that's
        // conceptually about MERCHANDISER/APPROVER only.
        List<UserProject> rows = userProjectService.getRolesInProject(projectId).stream()
                .filter(up -> up.getRole() == User.Role.MERCHANDISER || up.getRole() == User.Role.APPROVER)
                .collect(Collectors.toList());
        Map<String, User> usersById = userService.getAllUsers().stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        List<Map<String, Object>> result = rows.stream()
                .map(up -> {
                    User u = usersById.get(up.getUserId());
                    Map<String, Object> row = new HashMap<>();
                    row.put("userId", up.getUserId());
                    row.put("role", up.getRole().name());
                    row.put("username", u != null ? u.getUsername() : up.getUserId());
                    row.put("email", u != null && u.getEmail() != null ? u.getEmail() : "");
                    return row;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String roleStr = body.get("role");

        User.Role role;
        try {
            role = User.Role.valueOf(roleStr.toUpperCase());
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid role. Must be one of: VIEWER, MERCHANDISER, APPROVER, ADMIN"));
        }

        try {
            User user = userService.createUser(username, password, role, body.get("email"), body.get("displayName"));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "id", user.getId(),
                            "username", user.getUsername(),
                            "role", user.getRole().name()
                    ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * NR-65: admin invites a user by email instead of setting a password
     * directly. ADMIN-only, same as createUser — a PROJECT_ADMIN may assign
     * project roles on projects they administer (see canManageProjectRoles
     * above) but per NR-121's explicit instruction never creates accounts.
     * Falls under the general /api/v1/users/** ADMIN-only matcher, no
     * SecurityConfig change needed.
     */
    @PostMapping("/invite")
    public ResponseEntity<?> inviteUser(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String email = body.get("email");
        String roleStr = body.get("role");
        User.Role role;
        try {
            role = User.Role.valueOf(roleStr.toUpperCase());
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid role. Must be one of: VIEWER, MERCHANDISER, APPROVER, ADMIN"));
        }
        try {
            User user = userService.inviteUser(username, email, role, body.get("displayName"));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("id", user.getId(), "username", user.getUsername(), "role", user.getRole().name()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── NR-65: self-service profile — deliberately a separate matcher group
    // (/api/v1/users/me/**) from the ADMIN-only bulk user endpoints above,
    // same pattern as /api/v1/auth/sessions — available to every real
    // dashboard role for their OWN account only.

    @GetMapping("/me")
    public ResponseEntity<?> getMyProfile() {
        User user = currentUser();
        return ResponseEntity.ok(Map.of(
                "id", user.getId(), "username", user.getUsername(), "role", user.getRole().name(),
                "email", user.getEmail() != null ? user.getEmail() : "",
                "displayName", user.getDisplayName() != null ? user.getDisplayName() : "",
                "emailVerified", user.isEmailVerified()));
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateMyProfile(@RequestBody Map<String, String> body) {
        User user = userService.updateProfile(currentUser().getId(), body.get("displayName"), body.get("email"));
        return ResponseEntity.ok(Map.of(
                "id", user.getId(), "username", user.getUsername(),
                "email", user.getEmail() != null ? user.getEmail() : "",
                "displayName", user.getDisplayName() != null ? user.getDisplayName() : "",
                "emailVerified", user.isEmailVerified()));
    }

    @PostMapping("/me/change-password")
    public ResponseEntity<?> changeMyPassword(@RequestBody Map<String, String> body) {
        try {
            userService.changePassword(currentUser().getId(), body.get("currentPassword"), body.get("newPassword"));
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userService.findByUsername(username).orElseThrow();
    }
}
