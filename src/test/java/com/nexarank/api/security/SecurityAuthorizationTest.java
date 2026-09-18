// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.security;

import com.nexarank.api.controller.MerchRuleController;
import com.nexarank.api.controller.RuleEnrichmentController;
import com.nexarank.api.model.EnrichedQuery;
import com.nexarank.api.model.MerchRule;
import com.nexarank.api.service.ApiAccessLogService;
import com.nexarank.api.service.FacetConfigService;
import com.nexarank.api.service.MerchRuleService;
import com.nexarank.api.service.RuleEnrichmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end authorization tests through the REAL security filter chain:
 * SecurityConfig's request matchers + JwtAuthFilter's JWT parsing/claim
 * extraction, using the REAL JwtUtil to mint tokens (nothing about the
 * security layer itself is mocked — only the controllers' business
 * services are, via @WebMvcTest, so no database is needed).
 *
 * Two controllers are used as representative endpoints:
 * - MerchRuleController (/api/v1/rules/**) — role-gated, authenticated.
 * - RuleEnrichmentController (/api/v1/rules/enrich) — permitAll(), the
 *   endpoint every customer search service calls on every live query.
 */
@WebMvcTest(controllers = {MerchRuleController.class, RuleEnrichmentController.class})
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtUtil.class})
@TestPropertySource(properties = {
        "nexarank.jwt.secret=test-only-signing-key-not-used-anywhere-real-0123456789",
        "nexarank.jwt.expiration-ms=900000",
        "nexarank.internal.api-key=test-only-internal-key-not-used-anywhere-real"
})
class SecurityAuthorizationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;

    @MockitoBean private MerchRuleService merchRuleService;
    @MockitoBean private RuleEnrichmentService ruleEnrichmentService;
    // ApiAccessLogFilter (@Component Filter) is picked up by @WebMvcTest's
    // component scan and needs this dependency to construct, even though
    // it's not itself under test here.
    @MockitoBean private ApiAccessLogService apiAccessLogService;
    // NexaRankApplication's seedFacets() ApplicationRunner @Bean is picked
    // up too, since @WebMvcTest uses NexaRankApplication as its nearest
    // @SpringBootConfiguration — needs this to construct, never actually runs.
    @MockitoBean private FacetConfigService facetConfigService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    private String tokenFor(List<String> roles, String tenantId, String projectId) {
        return jwtUtil.generateToken("test-user", roles, tenantId, projectId, List.of());
    }

    // ── Requirement 2: VIEWER cannot perform writes gated to higher roles ────

    @Test
    void viewer_cannotCreateRule_forbidden() throws Exception {
        String token = tokenFor(List.of("VIEWER"), "tenant-a", "project-a");

        mockMvc.perform(post("/api/v1/rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"type\":\"BOOST\",\"query\":\"battery\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void viewer_canReadRules_ok() throws Exception {
        String token = tokenFor(List.of("VIEWER"), "tenant-a", "project-a");
        when(merchRuleService.getAllRules()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/rules").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void viewer_cannotDeleteRule_forbidden() throws Exception {
        String token = tokenFor(List.of("VIEWER"), "tenant-a", "project-a");

        mockMvc.perform(delete("/api/v1/rules/some-id").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void merchandiser_canCreateRule_butCannotPromote_adminOnly() throws Exception {
        String token = tokenFor(List.of("MERCHANDISER"), "tenant-a", "project-a");
        MerchRule created = new MerchRule();
        created.setId("new-rule");
        when(merchRuleService.createRule(any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"type\":\"BOOST\",\"query\":\"battery\"}"))
                .andExpect(status().isCreated());

        // Promote is ADMIN/TENANT_ADMIN only — MERCHANDISER must not pass,
        // even though they can create/edit.
        mockMvc.perform(patch("/api/v1/rules/some-id/promote").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ── Requirement 3: tampered / missing tenant claim must not silently ─────
    // ── default to an accessible scope ───────────────────────────────────────

    @Test
    void tamperedSignature_isRejected_notAuthenticatedAtAll() throws Exception {
        String valid = tokenFor(List.of("ADMIN"), "tenant-a", "project-a");
        // Flip one character in the signature segment — same header/payload,
        // broken signature. A real forgery attempt looks exactly like this.
        String[] parts = valid.split("\\.");
        String tamperedSig = (parts[2].charAt(0) == 'A' ? 'B' : 'A') + parts[2].substring(1);
        String tampered = parts[0] + "." + parts[1] + "." + tamperedSig;

        mockMvc.perform(get("/api/v1/rules").header("Authorization", "Bearer " + tampered))
                // No valid authentication at all -> falls through to anyRequest().denyAll()
                // as an anonymous request, i.e. 403, not treated as any authenticated role.
                .andExpect(status().isForbidden());
    }

    @Test
    void missingAuthorizationHeader_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/rules"))
                .andExpect(status().isForbidden());
    }

    /**
     * Regression test for a gap found while writing this suite:
     * JwtUtil.extractTenantId()/extractProjectId() used to return the literal
     * strings "default"/"main" when the claim was absent from an otherwise
     * validly-signed token, instead of rejecting the request. "default"/"main"
     * aren't placeholders — they're the actual seeded tenant and project in
     * this system (see V1__init_schema.sql) — so this wasn't a generic
     * fail-open, it was a fail-open onto a specific, real, live tenant/project.
     * Fixed: both extractors now return null on a missing claim, and
     * JwtAuthFilter treats that as an invalid token (same as a bad signature),
     * never setting authentication/TenantContext for it.
     */
    @Test
    void jwtMissingTenantClaim_mustBeRejected_notDefaultedToDefaultTenant() throws Exception {
        // Mint a validly-signed token with NO tenantId/projectId claims at all
        // (JwtUtil.generateToken always sets them, so this token is built by
        // hand with the same key/signature mechanism to simulate a token type
        // that never carried these claims in the first place).
        String noClaimsToken = io.jsonwebtoken.Jwts.builder()
                .subject("test-user")
                .claim("roles", List.of("ADMIN"))
                .claim("permissions", List.of())
                .issuedAt(new java.util.Date())
                .expiration(new java.util.Date(System.currentTimeMillis() + 900_000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        "test-only-signing-key-not-used-anywhere-real-0123456789"
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        when(merchRuleService.getAllRules()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/rules").header("Authorization", "Bearer " + noClaimsToken))
                .andExpect(status().isForbidden());
    }

    // ── Requirement 4: SUPER_ADMIN must not silently grant elevated access ───

    @Test
    void superAdminAlone_withoutAdmin_cannotAccessAdminOnlyEndpoints() throws Exception {
        // SUPER_ADMIN exists in the enum and is used alongside ADMIN in a
        // couple of audit-log matchers (documented, intentional), but must
        // grant NOTHING beyond that. hasRole("ADMIN") matchers (engine-config,
        // groups, users, facets write) must reject a SUPER_ADMIN-only token
        // exactly like any other non-ADMIN role.
        String token = tokenFor(List.of("SUPER_ADMIN"), "tenant-a", "project-a");

        mockMvc.perform(get("/api/v1/engine-config/fields").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void superAdminAlone_cannotCreateOrDeleteRules_notAnImplicitAdmin() throws Exception {
        String token = tokenFor(List.of("SUPER_ADMIN"), "tenant-a", "project-a");

        mockMvc.perform(post("/api/v1/rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"type\":\"BOOST\",\"query\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    // ── /api/v1/rules/enrich: permitAll(), the highest-traffic public endpoint ─
    // ── Requirement 1 (translated to the unauthenticated case) ───────────────

    /**
     * Regression test for a gap found while writing this suite: /rules/enrich
     * is deliberately permitAll() (customer search services call it, not
     * logged-in users), and JwtAuthFilter used to set TenantContext from
     * X-Tenant-Id/X-Project-Id with NO verification at all — any caller could
     * claim to be any tenant on the single most-trafficked endpoint in the
     * product, and picked up ROLE_INTERNAL (write access to /clicks,
     * /search-events, /zero-results) in the process.
     *
     * Fixed: that header path now requires X-Api-Key to match
     * nexarank.internal.api-key. Without it, the header is ignored entirely —
     * TenantContext falls back to its own class-level default ("default"),
     * not to the caller-supplied value.
     */
    @Test
    void enrichEndpoint_tenantHeaderWithoutCorrectKey_isIgnored() throws Exception {
        AtomicReference<String> tenantSeenByService = new AtomicReference<>();
        when(ruleEnrichmentService.enrich(anyString(), any(), anyString(), any(), any()))
                .thenAnswer(inv -> {
                    tenantSeenByService.set(TenantContext.getTenantId());
                    return new EnrichedQuery();
                });

        mockMvc.perform(get("/api/v1/rules/enrich")
                        .param("query", "battery")
                        // No Authorization header, no (or wrong) X-Api-Key.
                        .header("X-Tenant-Id", "some-other-customers-tenant"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(tenantSeenByService.get())
                .as("an unverified X-Tenant-Id header must not be able to pick an arbitrary tenant")
                .isEqualTo("default");
    }

    @Test
    void enrichEndpoint_tenantHeaderWithWrongKey_isIgnored() throws Exception {
        AtomicReference<String> tenantSeenByService = new AtomicReference<>();
        when(ruleEnrichmentService.enrich(anyString(), any(), anyString(), any(), any()))
                .thenAnswer(inv -> {
                    tenantSeenByService.set(TenantContext.getTenantId());
                    return new EnrichedQuery();
                });

        mockMvc.perform(get("/api/v1/rules/enrich")
                        .param("query", "battery")
                        .header("X-Tenant-Id", "some-other-customers-tenant")
                        .header("X-Api-Key", "not-the-real-key"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(tenantSeenByService.get()).isEqualTo("default");
    }

    /** The legitimate case: search-api presenting the correct shared key must still work. */
    @Test
    void enrichEndpoint_tenantHeaderWithCorrectKey_isHonored() throws Exception {
        AtomicReference<String> tenantSeenByService = new AtomicReference<>();
        when(ruleEnrichmentService.enrich(anyString(), any(), anyString(), any(), any()))
                .thenAnswer(inv -> {
                    tenantSeenByService.set(TenantContext.getTenantId());
                    return new EnrichedQuery();
                });

        mockMvc.perform(get("/api/v1/rules/enrich")
                        .param("query", "battery")
                        .header("X-Tenant-Id", "legit-tenant")
                        .header("X-Project-Id", "legit-project")
                        .header("X-Api-Key", "test-only-internal-key-not-used-anywhere-real"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(tenantSeenByService.get()).isEqualTo("legit-tenant");
    }
}
