// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.model.User;
import com.nexarank.api.repository.UserGroupRepository;
import com.nexarank.api.repository.GroupPermissionRepository;
import com.nexarank.api.repository.UserGroupMembershipRepository;
import java.util.List;
import java.util.stream.Collectors;
import com.nexarank.api.security.JwtUtil;
import com.nexarank.api.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final UserGroupRepository userGroupRepository;
    private final GroupPermissionRepository groupPermissionRepository;
    private final UserGroupMembershipRepository membershipRepository;

    public AuthController(UserService userService, JwtUtil jwtUtil,
                          UserGroupRepository userGroupRepository,
                          GroupPermissionRepository groupPermissionRepository,
                          UserGroupMembershipRepository membershipRepository) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.userGroupRepository = userGroupRepository;
        this.groupPermissionRepository = groupPermissionRepository;
        this.membershipRepository = membershipRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        return userService.findByUsername(username)
                .filter(user -> userService.validatePassword(password, user.getPassword()))
                .filter(User::isEnabled)
                .map(user -> {
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
                    final List<String> finalPermissions = permissions;
                    return ResponseEntity.ok(Map.of(
                            "token", token,
                            "username", user.getUsername(),
                            "role", user.getRole().name(),
                            "tenantId", tenantId,
                            "projectId", projectId,
                            "groupId", user.getGroupId() != null ? user.getGroupId() : "",
                            "permissions", finalPermissions
                    ));
                })
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid username or password")));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
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
            User user = userService.createUser(username, password, role);
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
}
