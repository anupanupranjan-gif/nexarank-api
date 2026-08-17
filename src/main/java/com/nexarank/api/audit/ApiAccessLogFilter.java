// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.audit;

import com.nexarank.api.security.TenantContext;
import com.nexarank.api.service.ApiAccessLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * NR-70 Tier 2: records tenantId, userId, endpoint, params, response code and
 * latency for API requests.
 *
 * Runs after the JWT filter so TenantContext and the authenticated principal are
 * already populated — the log is only useful if it can attribute a call to a
 * tenant and a user.
 *
 * Deliberately skips the highest-volume, lowest-value paths: /rules/enrich and
 * /content/enrich are served on every storefront search and page render, and
 * logging them would generate orders of magnitude more audit rows than every
 * other endpoint combined while telling a compliance reader nothing about who
 * changed what. Health/metrics endpoints are skipped for the same reason.
 * Disable the whole filter with nexarank.audit.api-access-log.enabled=false.
 */
@Component
@Order(100)
public class ApiAccessLogFilter extends OncePerRequestFilter {

    private static final String[] SKIP_PREFIXES = {
            "/actuator",
            "/api/v1/rules/enrich",
            "/api/v1/content/enrich",
            "/api/v1/clicks",
            "/api/v1/search-events",
            "/error"
    };

    private final ApiAccessLogService accessLogService;
    private final boolean enabled;

    public ApiAccessLogFilter(ApiAccessLogService accessLogService,
                              @Value("${nexarank.audit.api-access-log.enabled:true}") boolean enabled) {
        this.accessLogService = accessLogService;
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) return true;
        String path = request.getRequestURI();
        for (String prefix : SKIP_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        // Only NexaRank's own API surface is audited; static assets are not.
        return !path.startsWith("/api/v1");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long started = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            try {
                // Read the context inside the finally block but before it is
                // cleared downstream — after the chain returns, the authenticated
                // principal is still present on this thread.
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                String username = auth != null ? auth.getName() : "anonymous";
                String tenantId = TenantContext.getTenantId();
                if (tenantId != null) {
                    accessLogService.recordAccess(
                            tenantId,
                            TenantContext.getProjectId(),
                            username,
                            request.getRequestURI(),
                            request.getMethod(),
                            request.getParameterMap(),
                            response.getStatus(),
                            System.currentTimeMillis() - started,
                            accessLogService.clientIp(request));
                }
            } catch (Exception ignored) {
                // Never let audit bookkeeping surface as a request failure.
            }
        }
    }
}
