// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.model.RefreshToken;
import com.nexarank.api.model.User;
import com.nexarank.api.repository.ProjectRepository;
import com.nexarank.api.repository.UserGroupRepository;
import com.nexarank.api.repository.GroupPermissionRepository;
import com.nexarank.api.repository.UserGroupMembershipRepository;
import java.util.List;
import java.util.stream.Collectors;
import com.nexarank.api.security.JwtUtil;
import com.nexarank.api.service.ProjectAccessService;
import com.nexarank.api.service.RefreshTokenService;
import com.nexarank.api.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/**
 * NR-120: login now also issues a revocable refresh token (HttpOnly cookie,
 * session/device identity only — see RefreshToken) alongside the short-lived
 * access token. /refresh exchanges a valid, unexpired refresh cookie for a
 * new access token, rotating the refresh token in the process. /logout
 * revokes the current session server-side rather than just forgetting the
 * access token client-side.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refreshToken";
    private static final String REFRESH_COOKIE_PATH = "/nexarank/api/v1/auth";

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final UserGroupRepository userGroupRepository;
    private final GroupPermissionRepository groupPermissionRepository;
    private final UserGroupMembershipRepository membershipRepository;
    private final RefreshTokenService refreshTokenService;
    private final ProjectAccessService projectAccessService;
    private final ProjectRepository projectRepository;

    @Value("${nexarank.cookie.secure:false}")
    private boolean cookieSecure;

    public AuthController(UserService userService, JwtUtil jwtUtil,
                          UserGroupRepository userGroupRepository,
                          GroupPermissionRepository groupPermissionRepository,
                          UserGroupMembershipRepository membershipRepository,
                          RefreshTokenService refreshTokenService,
                          ProjectAccessService projectAccessService,
                          ProjectRepository projectRepository) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.userGroupRepository = userGroupRepository;
        this.groupPermissionRepository = groupPermissionRepository;
        this.membershipRepository = membershipRepository;
        this.refreshTokenService = refreshTokenService;
        this.projectAccessService = projectAccessService;
        this.projectRepository = projectRepository;
    }

    /**
     * NR-121 step 6 / NR-122: self-service list of the projects the caller
     * may currently switch into, backing the sidebar project switcher.
     * Deliberately separate from GET /admin/tenants/{id}/projects (ADMIN-only,
     * used by Analytics' cross-project reporting dropdown) — this one is
     * "what can I, personally, switch my own active project to," available
     * to every real dashboard role, not an admin capability.
     */
    @GetMapping("/available-projects")
    public ResponseEntity<?> availableProjects() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userService.findByUsername(username).orElseThrow();
        List<Map<String, String>> result = projectAccessService.availableProjectIds(user).stream()
                .map(projectRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .map(p -> Map.of("id", p.getId(), "name", p.getName()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body,
                                    HttpServletRequest request, HttpServletResponse response) {
        String username = body.get("username");
        String password = body.get("password");

        return userService.findByUsername(username)
                .filter(user -> userService.validatePassword(password, user.getPassword()))
                .filter(User::isEnabled)
                .<ResponseEntity<?>>map(user -> {
                    // NR-121: no project picker on the login form — resolve it
                    // server-side (last-active if still valid, else a sensible
                    // default) rather than exposing project names to an
                    // unauthenticated visitor.
                    String projectId = projectAccessService.resolveActiveProjectId(user);
                    if (projectId == null) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(Map.of("error", "No project access configured for this user"));
                    }
                    List<String> roles = projectAccessService.activateProject(user, projectId);
                    Map<String, Object> tokenResponse = buildAccessTokenResponse(user, projectId, roles);
                    issueRefreshCookie(user, request, response);
                    return ResponseEntity.ok(tokenResponse);
                })
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid username or password")));
    }

    /**
     * Public — a refresh token isn't a JWT, so it can't go through the
     * normal Authorization: Bearer flow JwtAuthFilter expects. This
     * endpoint does its own verification instead (hash lookup, expiry,
     * revocation), same shape as /login doing its own password check.
     *
     * NR-121: doubles as the project-switch endpoint. An optional
     * projectId in the body switches the active project (validated against
     * user_projects / tenant membership); omitted, it re-resolves whatever
     * the user last had active — refresh_tokens itself deliberately carries
     * no project context, so this is the one place that state lives.
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
                                      @RequestBody(required = false) Map<String, String> body,
                                      HttpServletRequest request, HttpServletResponse response) {
        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "No refresh token"));
        }
        RefreshToken activeRow = refreshTokenService.findActiveByRawToken(refreshToken).orElse(null);
        if (activeRow == null) {
            clearRefreshCookie(response);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid or expired refresh token"));
        }
        Optional<User> userOpt = userService.findById(activeRow.getUserId());
        if (userOpt.isEmpty()) {
            clearRefreshCookie(response);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User no longer exists"));
        }
        User user = userOpt.get();
        String requestedProjectId = body != null ? body.get("projectId") : null;
        String projectId = requestedProjectId != null ? requestedProjectId : projectAccessService.resolveActiveProjectId(user);
        if (projectId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No project access configured for this user"));
        }
        List<String> roles;
        try {
            roles = projectAccessService.activateProject(user, projectId);
        } catch (IllegalArgumentException e) {
            // NR-121 step 5 regression fix: validate the switch BEFORE rotating.
            // The original ordering rotated unconditionally, so a rejected
            // switch attempt still burned the presented refresh token server-
            // side without ever sending the client the new cookie value —
            // silently orphaning an otherwise-healthy session. Now a rejected
            // switch leaves the presented token untouched and reusable.
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
        RefreshTokenService.IssuedToken rotated = refreshTokenService
                .rotate(refreshToken, deviceInfo(request), clientIp(request))
                .orElse(null);
        if (rotated == null) {
            clearRefreshCookie(response);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid or expired refresh token"));
        }
        setRefreshCookie(response, rotated.rawToken());
        return ResponseEntity.ok(buildAccessTokenResponse(user, projectId, roles));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
                                     HttpServletResponse response) {
        if (refreshToken != null) {
            refreshTokenService.findActiveByRawToken(refreshToken)
                    .ifPresent(row -> refreshTokenService.revokeOwn(row.getId(), row.getUserId()));
        }
        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String roleStr = body.get("role");
        String email = body.get("email");
        String displayName = body.get("displayName");

        User.Role role;
        try {
            role = User.Role.valueOf(roleStr != null ? roleStr.toUpperCase() : "VIEWER");
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid role. Must be one of: STAKEHOLDER, VIEWER, MERCHANDISER, APPROVER, ADMIN"));
        }

        try {
            User user = userService.createUser(username, password, role, email, displayName);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "id", user.getId(),
                            "username", user.getUsername(),
                            "role", user.getRole().name(),
                            "email", user.getEmail() != null ? user.getEmail() : "",
                            "displayName", user.getDisplayName() != null ? user.getDisplayName() : ""
                    ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private Map<String, Object> buildAccessTokenResponse(User user, String projectId, List<String> roles) {
        String tenantId = user.getTenantId() != null ? user.getTenantId() : "default";
        // Load permissions from ALL user groups (union)
        List<String> permissions = membershipRepository.findByUserId(user.getId())
                .stream()
                .flatMap(m -> groupPermissionRepository.findByGroupId(m.getGroupId()).stream())
                .map(gp -> gp.getPermission().name())
                .distinct()
                .collect(Collectors.toList());
        // Fallback to single group_id if no memberships
        if (permissions.isEmpty() && user.getGroupId() != null) {
            permissions = groupPermissionRepository.findByGroupId(user.getGroupId())
                    .stream().map(gp -> gp.getPermission().name()).collect(Collectors.toList());
        }
        String token = jwtUtil.generateToken(user.getUsername(), roles, tenantId, projectId, permissions);
        // "role" stays a single string for the existing frontend role-gating logic
        // (canCreate/canApprove/etc. all check a single auth.role) — the full list
        // is also included as "roles" for when PROJECT_ADMIN UI support is built.
        // Every role a real account holds today is single-valued in practice, so
        // roles.get(0) is exact, not an approximation, until PROJECT_ADMIN ships.
        String primaryRole = roles.isEmpty() ? user.getRole().name() : roles.get(0);
        return Map.of(
                "token", token,
                "username", user.getUsername(),
                "role", primaryRole,
                "roles", roles,
                "tenantId", tenantId,
                "projectId", projectId,
                "groupId", user.getGroupId() != null ? user.getGroupId() : "",
                "permissions", permissions
        );
    }

    private void issueRefreshCookie(User user, HttpServletRequest request, HttpServletResponse response) {
        String tenantId = user.getTenantId() != null ? user.getTenantId() : "default";
        RefreshTokenService.IssuedToken issued = refreshTokenService.issue(
                user.getId(), tenantId, deviceInfo(request), clientIp(request));
        setRefreshCookie(response, issued.rawToken());
    }

    private void setRefreshCookie(HttpServletResponse response, String rawToken) {
        Cookie cookie = new Cookie(REFRESH_COOKIE_NAME, rawToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath(REFRESH_COOKIE_PATH);
        cookie.setAttribute("SameSite", "Strict");
        cookie.setMaxAge((int) (refreshTokenService.getRefreshExpirationMs() / 1000));
        response.addCookie(cookie);
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(REFRESH_COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath(REFRESH_COOKIE_PATH);
        cookie.setAttribute("SameSite", "Strict");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private String deviceInfo(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
