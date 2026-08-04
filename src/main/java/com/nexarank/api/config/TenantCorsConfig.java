// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.config;

import com.nexarank.api.model.Tenant;
import com.nexarank.api.repository.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * NR-129: nexarank-api had zero CORS configuration anywhere — Spring's
 * DefaultCorsProcessor actively rejects any browser preflight (403 "Invalid
 * CORS request") once a request is mapped to a real endpoint but no
 * CorsConfigurationSource bean exists, which is exactly what blocked
 * AvinoShop's frontend from calling /content/enrich directly.
 *
 * Origins are NOT a hardcoded/wildcard allow-list: they're read from each
 * enabled Tenant's allowed_origins column (comma-separated), so enabling a
 * future tenant's storefront is a data change (PUT /admin/tenants/{id})
 * rather than a code change. Only an Origin that appears in some enabled
 * tenant's list is ever allowed — everything else gets no CORS headers at
 * all, same rejection behavior as today for anyone not on the list.
 *
 * The aggregated origin set is cached in memory and refreshed on a short
 * TTL rather than queried per request — this source is consulted on every
 * request once .cors(...) is wired in, including the /rules/enrich hot path
 * (documented <20ms p99 target), so a DB hit per request isn't acceptable.
 */
@Configuration
public class TenantCorsConfig {

    private static final long REFRESH_INTERVAL_MS = 30_000;

    private final TenantRepository tenantRepository;
    private final AtomicReference<Set<String>> cachedOrigins = new AtomicReference<>(Set.of());
    private volatile long lastRefreshed = 0L;

    public TenantCorsConfig(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        return this::resolveCorsConfiguration;
    }

    private CorsConfiguration resolveCorsConfiguration(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || !allowedOrigins().contains(origin)) {
            return null; // no CORS config for this origin — browser blocks it, same as today
        }

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(origin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Content-Type", "Accept", "Authorization",
                "X-Api-Key", "X-Tenant-Id", "X-Project-Id"));
        // Storefront callers (nexarank-content-sdk / SDK enrich clients) authenticate
        // via headers (X-Api-Key/X-Tenant-Id), never cookies, on these public endpoints.
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        return config;
    }

    private Set<String> allowedOrigins() {
        long now = System.currentTimeMillis();
        if (now - lastRefreshed > REFRESH_INTERVAL_MS) {
            refresh();
        }
        return cachedOrigins.get();
    }

    private synchronized void refresh() {
        if (System.currentTimeMillis() - lastRefreshed <= REFRESH_INTERVAL_MS) {
            return; // another thread already refreshed while this one waited
        }
        Set<String> origins = tenantRepository.findAll().stream()
                .filter(Tenant::isEnabled)
                .map(Tenant::getAllowedOrigins)
                .filter(csv -> csv != null && !csv.isBlank())
                .flatMap(csv -> Arrays.stream(csv.split(",")))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        cachedOrigins.set(origins);
        lastRefreshed = System.currentTimeMillis();
    }
}
