// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // public endpoints
                .requestMatchers("/api/v1/auth/login").permitAll()
                .requestMatchers("/api/v1/admin/public/**").permitAll()
                .requestMatchers("/api/v1/auth/register").hasRole("ADMIN")
                .requestMatchers("/actuator/health").permitAll()
                // rule enrichment — public, called by customer search services
                .requestMatchers("/api/v1/rules/enrich").permitAll()
                .requestMatchers("/api/v1/clicks").hasAnyRole("INTERNAL", "ADMIN", "MERCHANDISER", "APPROVER", "VIEWER")
                .requestMatchers("/api/v1/zero-results").hasAnyRole("INTERNAL", "ADMIN")
                .requestMatchers("/api/v1/search-events").hasAnyRole("INTERNAL", "ADMIN")
                // read access — all authenticated roles
                .requestMatchers(HttpMethod.GET, "/api/v1/rules/**").hasAnyRole("VIEWER", "MERCHANDISER", "APPROVER", "ADMIN")
                // create and edit — merchandiser and above
                .requestMatchers(HttpMethod.POST, "/api/v1/rules/**").hasAnyRole("MERCHANDISER", "APPROVER", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/rules/**").hasAnyRole("MERCHANDISER", "APPROVER", "ADMIN")
                // toggle and delete — approver and above
                .requestMatchers(HttpMethod.PATCH, "/api/v1/rules/**").hasAnyRole("APPROVER", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/rules/**").hasAnyRole("APPROVER", "ADMIN")
                // tenant and project management — admin only
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                // user groups — admin only
                .requestMatchers("/api/v1/groups/**").hasRole("ADMIN")
                // user management — admin only
                .requestMatchers("/api/v1/users/**").hasRole("ADMIN")
                // facet config — read for all, write for admin
                .requestMatchers(HttpMethod.GET, "/api/v1/facets/**").hasAnyRole("VIEWER", "MERCHANDISER", "APPROVER", "ADMIN")
                .requestMatchers("/api/v1/facets/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/click-intelligence/**").hasAnyRole("VIEWER", "MERCHANDISER", "APPROVER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/search-quality/**").hasAnyRole("VIEWER", "MERCHANDISER", "APPROVER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/search-quality/run").hasRole("ADMIN")
                .requestMatchers("/api/v1/engine-config/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/audit/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/analytics/**").hasAnyRole("ADMIN", "APPROVER", "MERCHANDISER", "VIEWER")
                .requestMatchers("/api/v1/judgments/**").hasAnyRole("ADMIN", "APPROVER", "MERCHANDISER")
                .requestMatchers(HttpMethod.GET, "/api/v1/ab-tests/**").hasAnyRole("VIEWER", "MERCHANDISER", "APPROVER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/ab-tests/**").hasAnyRole("MERCHANDISER", "APPROVER", "ADMIN")
                .requestMatchers("/api/v1/suggestions/**").hasAnyRole("ADMIN", "APPROVER", "MERCHANDISER")
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
