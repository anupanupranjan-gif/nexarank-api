// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${nexarank.jwt.secret}")
    private String secret;

    @Value("${nexarank.jwt.expiration-ms}")
    private long expirationMs;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * NR-121: roles is a list, not a single string — a user can hold more
     * than one project-scoped role on the same project (PROJECT_ADMIN =
     * MERCHANDISER + APPROVER together, not a fourth stored role value).
     * JwtAuthFilter grants one Spring Security authority per entry, so
     * hasRole/hasAnyRole checks work identically whether a token carries
     * one role or several.
     */
    public String generateToken(String username, List<String> roles, String tenantId, String projectId, List<String> permissions) {
        return Jwts.builder()
                .subject(username)
                .claim("roles", roles)
                .claim("tenantId", tenantId)
                .claim("projectId", projectId)
                .claim("permissions", permissions)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        Object roles = parseClaims(token).get("roles");
        if (roles instanceof List) return (List<String>) roles;
        return List.of();
    }

    @SuppressWarnings("unchecked")
    public List<String> extractPermissions(String token) {
        Object perms = parseClaims(token).get("permissions");
        if (perms instanceof List) return (List<String>) perms;
        return List.of();
    }

    public String extractTenantId(String token) {
        String tenantId = parseClaims(token).get("tenantId", String.class);
        return tenantId != null ? tenantId : "default";
    }

    public String extractProjectId(String token) {
        String projectId = parseClaims(token).get("projectId", String.class);
        return projectId != null ? projectId : "main";
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
