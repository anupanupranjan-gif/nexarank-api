// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    /**
     * Shared secret gating the internal-service header path below (X-Tenant-Id/
     * X-Project-Id + X-Api-Key). Blank/unset (the property's own default)
     * disables that path entirely — fails closed, never "accept any caller"
     * — rather than only failing closed once someone remembers to set it.
     */
    @Value("${nexarank.internal.api-key:}")
    private String internalApiKey;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // Internal service calls (from search-api / nexarank-click-consumer).
            // Gated behind a shared secret (X-Api-Key) matched against
            // nexarank.internal.api-key — previously this trusted X-Tenant-Id/
            // X-Project-Id from ANY caller with no verification at all, which
            // meant an unauthenticated request to a permitAll() endpoint (e.g.
            // /rules/enrich) could claim to be any tenant, and also picked up
            // ROLE_INTERNAL, which SecurityConfig grants write access to
            // /clicks, /search-events, /zero-results with. A caller that
            // doesn't present the correct key is simply not granted this path
            // at all — the request continues as anonymous, same as if neither
            // header were present.
            String tenantIdHeader = request.getHeader("X-Tenant-Id");
            String projectIdHeader = request.getHeader("X-Project-Id");
            String presentedInternalKey = request.getHeader("X-Api-Key");
            boolean internalKeyConfigured = internalApiKey != null && !internalApiKey.isBlank();
            if (tenantIdHeader != null && internalKeyConfigured && internalApiKey.equals(presentedInternalKey)) {
                TenantContext.setTenantId(tenantIdHeader);
                TenantContext.setProjectId(projectIdHeader != null ? projectIdHeader : "main");
                TenantContext.setPermissions(java.util.List.of());
                UsernamePasswordAuthenticationToken internalAuth =
                    new UsernamePasswordAuthenticationToken(
                        "internal-service", null,
                        List.of(new SimpleGrantedAuthority("ROLE_INTERNAL")));
                SecurityContextHolder.getContext().setAuthentication(internalAuth);
            }

            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                if (jwtUtil.isTokenValid(token)) {
                    String username = jwtUtil.extractUsername(token);
                    List<String> roles = jwtUtil.extractRoles(token);
                    String tenantId = jwtUtil.extractTenantId(token);
                    String projectId = jwtUtil.extractProjectId(token);

                    // A validly-signed token missing either claim is treated as
                    // not authenticated at all, same as a bad signature — never
                    // silently scoped to some default tenant/project (see
                    // JwtUtil.extractTenantId/extractProjectId javadoc).
                    if (tenantId != null && !tenantId.isBlank() && projectId != null && !projectId.isBlank()) {
                        TenantContext.setTenantId(tenantId);
                        TenantContext.setProjectId(projectId);
                        TenantContext.setPermissions(jwtUtil.extractPermissions(token));

                        // NR-121: a token can carry more than one role (PROJECT_ADMIN =
                        // MERCHANDISER + APPROVER together) — one Spring Security authority
                        // per role, so hasRole/hasAnyRole work identically either way.
                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(
                                        username,
                                        null,
                                        roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).collect(Collectors.toList())
                                );
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            // Always clear tenant context after request completes
            TenantContext.clear();
        }
    }
}
