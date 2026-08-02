// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.model.User;
import com.nexarank.api.repository.UserGroupRepository;
import com.nexarank.api.repository.GroupPermissionRepository;
import com.nexarank.api.repository.UserGroupMembershipRepository;
import java.util.List;
import java.util.stream.Collectors;
import com.nexarank.api.security.JwtUtil;
import com.nexarank.api.service.RefreshTokenService;
import com.nexarank.api.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
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

    @Value("${nexarank.cookie.secure:false}")
    private boolean cookieSecure;

    public AuthController(UserService userService, JwtUtil jwtUtil,
                          UserGroupRepository userGroupRepository,
                          GroupPermissionRepository groupPermissionRepository,
                          UserGroupMembershipRepository membershipRepository,
                          RefreshTokenService refreshTokenService) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.userGroupRepository = userGroupRepository;
        this.groupPermissionRepository = groupPermissionRepository;
        this.membershipRepository = membershipRepository;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body,
                                    HttpServletRequest request, HttpServletResponse response) {
        String username = body.get("username");
        String password = body.get("password");

        return userService.findByUsername(username)
                .filter(user -> userService.validatePassword(password, user.getPassword()))
                .filter(User::isEnabled)
                .map(user -> {
                    Map<String, Object> tokenResponse = buildAccessTokenResponse(user);
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
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
                                      HttpServletRequest request, HttpServletResponse response) {
        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "No refresh token"));
        }
        RefreshTokenService.IssuedToken rotated = refreshTokenService
                .rotate(refreshToken, deviceInfo(request), clientIp(request))
                .orElse(null);
        if (rotated == null) {
            clearRefreshCookie(response);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid or expired refresh token"));
        }
        Optional<User> user = userService.findById(rotated.row().getUserId());
        if (user.isEmpty()) {
            clearRefreshCookie(response);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User no longer exists"));
        }
        setRefreshCookie(response, rotated.rawToken());
        return ResponseEntity.ok(buildAccessTokenResponse(user.get()));
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

    private Map<String, Object> buildAccessTokenResponse(User user) {
        String tenantId = user.getTenantId() != null ? user.getTenantId() : "default";
        String projectId = "main";
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
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole().name(), tenantId, projectId, permissions);
        return Map.of(
                "token", token,
                "username", user.getUsername(),
                "role", user.getRole().name(),
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
