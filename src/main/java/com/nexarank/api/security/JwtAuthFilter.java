// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // Internal service calls (from nexarank-click-consumer)
            String tenantIdHeader = request.getHeader("X-Tenant-Id");
            String projectIdHeader = request.getHeader("X-Project-Id");
            if (tenantIdHeader != null) {
                TenantContext.setTenantId(tenantIdHeader);
                TenantContext.setProjectId(projectIdHeader != null ? projectIdHeader : "main");
                TenantContext.setPermissions(java.util.List.of());
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken internalAuth =
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "internal-service", null,
                        java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_INTERNAL")));
                SecurityContextHolder.getContext().setAuthentication(internalAuth);
            }

            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                if (jwtUtil.isTokenValid(token)) {
                    String username = jwtUtil.extractUsername(token);
                    String role = jwtUtil.extractRole(token);
                    String tenantId = jwtUtil.extractTenantId(token);
                    String projectId = jwtUtil.extractProjectId(token);

                    // Set tenant context for this request
                    TenantContext.setTenantId(tenantId);
                    TenantContext.setProjectId(projectId);
                    TenantContext.setPermissions(jwtUtil.extractPermissions(token));

                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(
                                    username,
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + role))
                            );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            // Always clear tenant context after request completes
            TenantContext.clear();
        }
    }
}
