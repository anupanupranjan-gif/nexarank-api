// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            // NR-129: applies the TenantCorsConfig.corsConfigurationSource() bean —
            // covers every endpoint generically (not a per-endpoint patch), but only
            // ever grants CORS headers to an Origin present in some enabled tenant's
            // allowed_origins list; every other Origin still gets rejected exactly
            // as before.
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // public endpoints
                .requestMatchers("/api/v1/auth/login").permitAll()
                // NR-120: neither endpoint's real credential is a JWT (it's the
                // refresh-token cookie), so neither goes through the normal
                // Authorization: Bearer flow — each does its own verification
                // (hash lookup, expiry, revocation) instead, same shape as login.
                // Logout in particular must work even when the access token has
                // already expired (15 min) — that's the exact scenario it exists
                // to handle, so gating it behind a valid Bearer token would be
                // self-defeating.
                .requestMatchers("/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                // NR-65: invite/reset/verify links are each their own credential
                // (a single-use emailed token, not a JWT) — same public + self-
                // verifying shape as /refresh above, not gated behind Bearer auth.
                .requestMatchers("/api/v1/auth/accept-invite", "/api/v1/auth/forgot-password",
                        "/api/v1/auth/reset-password", "/api/v1/auth/verify-email",
                        "/api/v1/auth/resend-verification").permitAll()
                .requestMatchers("/api/v1/admin/public/**").permitAll()
                .requestMatchers("/api/v1/auth/register").hasRole("ADMIN")
                // NR-120: self-service session listing/revoke-by-id needs to know
                // who's asking (there's no cookie-only path for that, unlike
                // logout's single-session revoke) — every real dashboard role,
                // including STAKEHOLDER (email-only access still means a real
                // login session).
                .requestMatchers("/api/v1/auth/sessions/**")
                        .hasAnyRole("STAKEHOLDER", "VIEWER", "MERCHANDISER", "APPROVER", "ADMIN")
                // NR-121 step 6 / NR-122: self-service "which projects can I switch
                // into" list backing the sidebar project switcher — same role set
                // as sessions above, every real dashboard role.
                .requestMatchers("/api/v1/auth/available-projects")
                        .hasAnyRole("STAKEHOLDER", "VIEWER", "MERCHANDISER", "APPROVER", "ADMIN")
                // observability: health + Prometheus scrape endpoint are open
                // (metrics only, no secrets; scraped in-cluster by Prometheus)
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                // rule enrichment — public, called by customer search services
                .requestMatchers("/api/v1/rules/enrich").permitAll()
                // content enrichment (NR-83) — public, called by customer storefronts
                .requestMatchers("/api/v1/content/enrich").permitAll()
                // content rules (NR-84) — same role shape as merch rules
                .requestMatchers(HttpMethod.GET, "/api/v1/content-rules/**").hasAnyRole("VIEWER", "MERCHANDISER", "APPROVER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/content-rules/*/submit").hasAnyRole("MERCHANDISER", "APPROVER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/content-rules/*/approve").hasAnyRole("APPROVER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/content-rules/*/reject").hasAnyRole("APPROVER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/content-rules/**").hasAnyRole("MERCHANDISER", "APPROVER", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/content-rules/**").hasAnyRole("MERCHANDISER", "APPROVER", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/content-rules/**").hasAnyRole("APPROVER", "ADMIN")
                .requestMatchers("/api/v1/clicks").hasAnyRole("INTERNAL", "ADMIN", "MERCHANDISER", "APPROVER", "VIEWER")
                .requestMatchers("/api/v1/zero-results").hasAnyRole("INTERNAL", "ADMIN")
                .requestMatchers("/api/v1/search-events").hasAnyRole("INTERNAL", "ADMIN")
                // NR-59: called by search-api synchronously right after its own
                // zero-hit search — same internal-service shape as the two
                // matchers above, must be matched before the general
                // /api/v1/suggestions/** rule below (first-match-wins).
                .requestMatchers("/api/v1/suggestions/zero-result-recovery").hasAnyRole("INTERNAL", "ADMIN")
                // read access — all authenticated roles
                .requestMatchers(HttpMethod.GET, "/api/v1/rules/**").hasAnyRole("VIEWER", "MERCHANDISER", "APPROVER", "ADMIN")
                // create and edit — merchandiser and above
                .requestMatchers(HttpMethod.POST, "/api/v1/rules/**").hasAnyRole("MERCHANDISER", "APPROVER", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/rules/**").hasAnyRole("MERCHANDISER", "APPROVER", "ADMIN")
                // submit for review — creator (merchandiser and above)
                .requestMatchers(HttpMethod.PATCH, "/api/v1/rules/*/submit").hasAnyRole("MERCHANDISER", "APPROVER", "ADMIN")
                // manual publish/revert of live rules — admin and tenant admin only
                .requestMatchers(HttpMethod.PATCH, "/api/v1/rules/*/promote").hasAnyRole("ADMIN", "TENANT_ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/rules/*/demote").hasAnyRole("ADMIN", "TENANT_ADMIN")
                // toggle, approve, reject, and delete — approver and above
                .requestMatchers(HttpMethod.PATCH, "/api/v1/rules/**").hasAnyRole("APPROVER", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/rules/**").hasAnyRole("APPROVER", "ADMIN")
                // tenant and project management — admin only
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                // user groups — admin only
                .requestMatchers("/api/v1/groups/**").hasRole("ADMIN")
                // NR-121: project-role assignment/removal additionally allows a
                // PROJECT_ADMIN caller — this matcher only establishes coarse
                // "holds some project-scoped role" presence; UserController does
                // the fine-grained check that they administer the SPECIFIC target
                // project before allowing the write. Read (GET) stays admin-only,
                // unchanged — this is scoped to the write path only, per the ask.
                .requestMatchers(HttpMethod.POST, "/api/v1/users/*/projects/**").hasAnyRole("ADMIN", "MERCHANDISER", "APPROVER")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/users/*/projects/**").hasAnyRole("ADMIN", "MERCHANDISER", "APPROVER")
                // NR-121 step 7: same pattern as above — coarse role presence here,
                // UserController.canManageProjectRoles does the real per-project
                // check (ADMIN, or PROJECT_ADMIN of the specific target project).
                .requestMatchers(HttpMethod.GET, "/api/v1/users/directory").hasAnyRole("ADMIN", "MERCHANDISER", "APPROVER")
                .requestMatchers(HttpMethod.GET, "/api/v1/users/project-roster/**").hasAnyRole("ADMIN", "MERCHANDISER", "APPROVER")
                // NR-65: self-service profile for the caller's OWN account —
                // every real dashboard role, same set as /auth/sessions. Must be
                // matched before the general /api/v1/users/** ADMIN-only fallback
                // below (first-match-wins).
                .requestMatchers("/api/v1/users/me/**")
                        .hasAnyRole("STAKEHOLDER", "VIEWER", "MERCHANDISER", "APPROVER", "ADMIN")
                // user management (create/delete accounts, groups) — admin only
                .requestMatchers("/api/v1/users/**").hasRole("ADMIN")
                // facet config — read for all, write for admin
                .requestMatchers(HttpMethod.GET, "/api/v1/facets/**").hasAnyRole("VIEWER", "MERCHANDISER", "APPROVER", "ADMIN")
                .requestMatchers("/api/v1/facets/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/click-intelligence/**").hasAnyRole("VIEWER", "MERCHANDISER", "APPROVER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/search-quality/**").hasAnyRole("VIEWER", "MERCHANDISER", "APPROVER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/search-quality/run").hasRole("ADMIN")
                .requestMatchers("/api/v1/engine-config/**").hasRole("ADMIN")
                // NR-70: Tier 1 (rule-change history) is visible to MERCHANDISER and
                // above and is project-scoped inside AuditQueryService via
                // user_projects. Everything else under /audit stays Tier 2 /
                // ADMIN-only — approval reasons, API access patterns and auth
                // failures are security-posture data, least privilege.
                .requestMatchers("/api/v1/audit/rule-changes/**")
                    .hasAnyRole("MERCHANDISER", "APPROVER", "ADMIN", "TENANT_ADMIN", "SUPER_ADMIN")
                .requestMatchers("/api/v1/audit/**").hasAnyRole("ADMIN", "TENANT_ADMIN", "SUPER_ADMIN")
                .requestMatchers("/api/v1/analytics/**").hasAnyRole("ADMIN", "APPROVER", "MERCHANDISER", "VIEWER")
                .requestMatchers("/api/v1/judgments/**").hasAnyRole("ADMIN", "APPROVER", "MERCHANDISER")
                .requestMatchers(HttpMethod.GET, "/api/v1/ab-tests/**").hasAnyRole("VIEWER", "MERCHANDISER", "APPROVER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/ab-tests/**").hasAnyRole("MERCHANDISER", "APPROVER", "ADMIN")
                .requestMatchers("/api/v1/suggestions/**").hasAnyRole("ADMIN", "APPROVER", "MERCHANDISER")
                // pipeline config — stopwords, stage config
                .requestMatchers(HttpMethod.GET, "/api/v1/pipeline/**").hasAnyRole("VIEWER", "MERCHANDISER", "APPROVER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pipeline/stages/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pipeline/stopwords/**").hasAnyRole("MERCHANDISER", "APPROVER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pipeline/stopwords/bulk").hasAnyRole("MERCHANDISER", "APPROVER", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/pipeline/**").hasAnyRole("APPROVER", "ADMIN")
                // LLM config — admin only for write, all authenticated for read
                .requestMatchers(HttpMethod.GET, "/api/v1/llm-config/**").hasAnyRole("VIEWER", "MERCHANDISER", "APPROVER", "ADMIN")
                .requestMatchers("/api/v1/llm-config/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/suggestions/config").hasAnyRole("VIEWER", "MERCHANDISER", "APPROVER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/suggestions/config").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/suggestions/watched-queries").hasAnyRole("VIEWER", "MERCHANDISER", "APPROVER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/suggestions/watched-queries").hasAnyRole("MERCHANDISER", "APPROVER", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/suggestions/watched-queries/**").hasAnyRole("MERCHANDISER", "APPROVER", "ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/suggestions/watched-queries/**").hasAnyRole("MERCHANDISER", "APPROVER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/signals/**").hasAnyRole("VIEWER", "MERCHANDISER", "APPROVER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/signals/**").hasAnyRole("MERCHANDISER", "APPROVER", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/signals/**").hasAnyRole("APPROVER", "ADMIN")
                .requestMatchers("/api/v1/reports/**").hasRole("ADMIN")
                .anyRequest().authenticated()   // ← add this line

            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
