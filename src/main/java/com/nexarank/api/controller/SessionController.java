// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.model.RefreshToken;
import com.nexarank.api.model.User;
import com.nexarank.api.security.TenantContext;
import com.nexarank.api.service.RefreshTokenService;
import com.nexarank.api.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NR-120: self-service ("my sessions") under /auth/sessions, plus a
 * tenant-scoped admin view under /admin/sessions. Never returns tokenHash —
 * only what a person needs to recognize and revoke a session.
 */
@RestController
public class SessionController {

    private final RefreshTokenService refreshTokenService;
    private final UserService userService;

    public SessionController(RefreshTokenService refreshTokenService, UserService userService) {
        this.refreshTokenService = refreshTokenService;
        this.userService = userService;
    }

    @GetMapping("/api/v1/auth/sessions")
    public ResponseEntity<?> getMySessions() {
        User user = currentUser();
        List<Map<String, Object>> sessions = refreshTokenService.listActiveForUser(user.getId())
                .stream().map(this::toDto).toList();
        return ResponseEntity.ok(sessions);
    }

    @DeleteMapping("/api/v1/auth/sessions/{id}")
    public ResponseEntity<?> revokeMySession(@PathVariable String id) {
        User user = currentUser();
        boolean revoked = refreshTokenService.revokeOwn(id, user.getId());
        return revoked ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/api/v1/auth/sessions")
    public ResponseEntity<?> revokeAllMySessions() {
        User user = currentUser();
        refreshTokenService.revokeAllForUser(user.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/admin/sessions")
    public ResponseEntity<?> getTenantSessions() {
        List<Map<String, Object>> sessions = refreshTokenService.listActiveForTenant(TenantContext.getTenantId())
                .stream().map(this::toDto).toList();
        return ResponseEntity.ok(sessions);
    }

    @DeleteMapping("/api/v1/admin/sessions/{id}")
    public ResponseEntity<?> revokeTenantSession(@PathVariable String id) {
        boolean revoked = refreshTokenService.revokeAsAdmin(id, TenantContext.getTenantId());
        return revoked ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    private User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userService.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + username));
    }

    private Map<String, Object> toDto(RefreshToken row) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", row.getId());
        dto.put("userId", row.getUserId());
        dto.put("deviceInfo", row.getDeviceInfo());
        dto.put("ipAddress", row.getIpAddress());
        dto.put("issuedAt", row.getIssuedAt());
        dto.put("lastUsedAt", row.getLastUsedAt());
        dto.put("expiresAt", row.getExpiresAt());
        return dto;
    }
}
