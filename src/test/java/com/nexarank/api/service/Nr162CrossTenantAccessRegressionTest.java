// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarank.api.model.ContentRule;
import com.nexarank.api.model.FacetConfig;
import com.nexarank.api.model.MerchRule;
import com.nexarank.api.repository.ContentRuleRepository;
import com.nexarank.api.repository.FacetConfigRepository;
import com.nexarank.api.repository.MerchRuleRepository;
import com.nexarank.api.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * NR-162 (cross-project/tenant by-ID authorization bypass) — regression
 * suite. This is the exploit found by hand during NR-121 regression
 * testing, translated into mechanical tests: {@code repository.findById(id)}
 * had no tenant/project ownership check across MerchRuleService,
 * RuleAbTestService, ContentRuleService, FacetConfigService, and
 * FacetVisibilityService — any authenticated project-scoped user could
 * read and mutate another project's (or, for Content Rules and Facets,
 * another tenant's) rules/tests/facets just by knowing or guessing an id,
 * even though the corresponding LIST endpoints filtered correctly.
 *
 * Each test below reproduces the exact shape of the original exploit:
 * a caller is scoped (via TenantContext, exactly as JwtAuthFilter would
 * set it from a real JWT) to tenant A / project A, but the repository
 * mock returns a row that actually belongs to a different tenant or
 * project — simulating "the caller knows/guessed a real id belonging to
 * someone else." The fix means the service must treat that id as not
 * found, never as found-and-belongs-to-me.
 *
 * Covers MerchRuleService (the primary NR-162 subject) and
 * FacetConfigService/ContentRuleService as representative companions —
 * RuleAbTestService and FacetVisibilityService follow the identical
 * tenant+project findScopedById() pattern and were fixed in the same
 * commit, but aren't separately duplicated here.
 */
@ExtendWith(MockitoExtension.class)
class Nr162CrossTenantAccessRegressionTest {

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    // ── MerchRuleService ───────────────────────────────────────────────────

    @Mock private MerchRuleRepository merchRuleRepository;
    @Mock private RuleVersionService versionService;
    @Mock private RuleTriggerConditionService triggerService;
    @Mock private RuleNotificationService notificationService;
    @Mock private com.nexarank.api.repository.TenantRepository tenantRepository;
    @Mock private AuditService auditService;
    @Mock private AuditDiffService auditDiffService;
    @Mock private RulesCacheVersionService cacheVersionService;

    private MerchRuleService merchRuleService() {
        return new MerchRuleService(merchRuleRepository, versionService, triggerService,
                notificationService, tenantRepository, auditService, auditDiffService, cacheVersionService);
    }

    private MerchRule ruleOwnedBy(String tenantId, String projectId) {
        MerchRule rule = new MerchRule();
        rule.setId("rule-victim");
        rule.setTenantId(tenantId);
        rule.setProjectId(projectId);
        rule.setType(MerchRule.RuleType.BOOST);
        rule.setQuery("battery");
        rule.setStatus(MerchRule.RuleStatus.LIVE);
        return rule;
    }

    /**
     * THE exploit scenario: attacker is a legitimately authenticated,
     * legitimately project-scoped user for tenant-a/project-a (a real JWT,
     * real login — this is not a bypass of authentication, only of
     * authorization). They send GET /api/v1/rules/{id} with the id of a
     * rule that belongs to tenant-a/project-B — a sibling project in the
     * SAME tenant they have no assignment to. Before the NR-162 fix, this
     * returned the rule (repository.findById(id) alone, no ownership
     * check). After the fix, it must come back empty — same as a
     * genuinely nonexistent id, so existence isn't leaked either.
     */
    @Test
    void getById_crossProject_sameTenant_returnsEmpty_notTheVictimRule() {
        TenantContext.setTenantId("tenant-a");
        TenantContext.setProjectId("project-a");
        when(merchRuleRepository.findById("rule-victim"))
                .thenReturn(Optional.of(ruleOwnedBy("tenant-a", "project-B")));

        Optional<MerchRule> result = merchRuleService().getById("rule-victim");

        assertThat(result).isEmpty();
        // The trigger-condition lookup only happens for a rule the caller
        // actually owns — asserting it's never invoked catches a fix that
        // filters the RETURNED rule's fields but still leaks its existence
        // by populating child data before the filter runs.
        verify(triggerService, never()).getConditions(anyString());
    }

    /** Same exploit, cross-TENANT variant (the more severe of the two). */
    @Test
    void getById_crossTenant_returnsEmpty_notTheVictimRule() {
        TenantContext.setTenantId("tenant-a");
        TenantContext.setProjectId("project-a");
        when(merchRuleRepository.findById("rule-victim"))
                .thenReturn(Optional.of(ruleOwnedBy("tenant-VICTIM", "project-a")));

        assertThat(merchRuleService().getById("rule-victim")).isEmpty();
    }

    /**
     * NR-162's description explicitly says "read AND mutate" — a fix that
     * only scoped the read path (getById) but left write paths open would
     * still be exploitable. updateRule/toggleRule/deleteRule must reject
     * the same cross-project id.
     */
    @Test
    void updateRule_crossProject_doesNotMutateTheVictimRule() {
        TenantContext.setTenantId("tenant-a");
        TenantContext.setProjectId("project-a");
        when(merchRuleRepository.findById("rule-victim"))
                .thenReturn(Optional.of(ruleOwnedBy("tenant-a", "project-B")));

        MerchRule attackerPayload = new MerchRule();
        attackerPayload.setType(MerchRule.RuleType.BOOST);
        attackerPayload.setQuery("hijacked");

        Optional<MerchRule> result = merchRuleService().updateRule("rule-victim", attackerPayload);

        assertThat(result).isEmpty();
        verify(merchRuleRepository, never()).save(any());
    }

    @Test
    void toggleRule_crossProject_doesNotMutateTheVictimRule() {
        TenantContext.setTenantId("tenant-a");
        TenantContext.setProjectId("project-a");
        when(merchRuleRepository.findById("rule-victim"))
                .thenReturn(Optional.of(ruleOwnedBy("tenant-a", "project-B")));

        assertThat(merchRuleService().toggleRule("rule-victim")).isEmpty();
        verify(merchRuleRepository, never()).save(any());
    }

    /**
     * deleteRule's original bug (per the NR-162 fix commit) was worse than
     * the others: it called repository.deleteById(id) UNCONDITIONALLY after
     * the ownership-filtered ifPresent() block, so even a correctly-scoped
     * lookup didn't stop the delete itself from running. This pins that the
     * delete call is never issued for a cross-project id.
     */
    @Test
    void deleteRule_crossProject_doesNotDeleteTheVictimRule() {
        TenantContext.setTenantId("tenant-a");
        TenantContext.setProjectId("project-a");
        when(merchRuleRepository.findById("rule-victim"))
                .thenReturn(Optional.of(ruleOwnedBy("tenant-a", "project-B")));

        merchRuleService().deleteRule("rule-victim");

        verify(merchRuleRepository, never()).deleteById(anyString());
        verify(auditService, never()).logRuleChange(any(), any(), any(), any(), any());
    }

    /** Sanity check: the SAME id, when it genuinely belongs to the caller's own tenant+project, must still work. */
    @Test
    void getById_ownRule_sameTenantAndProject_isReturned() {
        TenantContext.setTenantId("tenant-a");
        TenantContext.setProjectId("project-a");
        MerchRule own = ruleOwnedBy("tenant-a", "project-a");
        when(merchRuleRepository.findById("rule-mine")).thenReturn(Optional.of(own));
        when(triggerService.getConditions("rule-mine")).thenReturn(java.util.List.of());

        assertThat(merchRuleService().getById("rule-mine")).isPresent();
    }

    // ── FacetConfigService ─────────────────────────────────────────────────

    @Mock private FacetConfigRepository facetConfigRepository;

    private FacetConfig facetOwnedBy(String tenantId, String projectId) {
        FacetConfig f = new FacetConfig();
        f.setId("facet-victim");
        f.setTenantId(tenantId);
        f.setProjectId(projectId);
        f.setFieldName("brand.keyword");
        f.setFacetType(FacetConfig.FacetType.TERMS);
        return f;
    }

    @Test
    void facetConfig_getById_crossProject_returnsEmpty() {
        TenantContext.setTenantId("tenant-a");
        TenantContext.setProjectId("project-a");
        when(facetConfigRepository.findById("facet-victim"))
                .thenReturn(Optional.of(facetOwnedBy("tenant-a", "project-B")));

        assertThat(new FacetConfigService(facetConfigRepository).getById("facet-victim")).isEmpty();
    }

    /**
     * deleteFacet's original bug was the starkest in the whole NR-162 set:
     * repository.deleteById(id) with NO lookup at all beforehand — any
     * ADMIN in any tenant could delete any facet in any other tenant by id,
     * no ownership check whatsoever.
     */
    @Test
    void facetConfig_deleteFacet_crossTenant_doesNotDelete() {
        TenantContext.setTenantId("tenant-a");
        TenantContext.setProjectId("project-a");
        when(facetConfigRepository.findById("facet-victim"))
                .thenReturn(Optional.of(facetOwnedBy("tenant-VICTIM", "project-x")));

        new FacetConfigService(facetConfigRepository).deleteFacet("facet-victim");

        verify(facetConfigRepository, never()).deleteById(anyString());
    }

    @Test
    void facetConfig_toggleFacet_crossProject_doesNotMutate() {
        TenantContext.setTenantId("tenant-a");
        TenantContext.setProjectId("project-a");
        when(facetConfigRepository.findById("facet-victim"))
                .thenReturn(Optional.of(facetOwnedBy("tenant-a", "project-B")));

        assertThat(new FacetConfigService(facetConfigRepository).toggleFacet("facet-victim")).isEmpty();
        verify(facetConfigRepository, never()).save(any());
    }

    // ── ContentRuleService ─────────────────────────────────────────────────
    // Content rules are the one-tier-worse case the NR-162 fix commit calls
    // out explicitly: they DO now carry project_id (V52), and current
    // findScopedById() filters on both tenantId and projectId — this pins
    // that project-level scoping, not just the tenant-level fix from the
    // original commit.

    @Mock private ContentRuleRepository contentRuleRepository;
    @Mock private ContentRuleVersionService contentRuleVersionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private ContentRule contentRuleOwnedBy(String tenantId, String projectId) {
        ContentRule r = new ContentRule();
        r.setId("content-rule-victim");
        r.setTenantId(tenantId);
        r.setProjectId(projectId);
        r.setStatus(ContentRule.ContentRuleStatus.ACTIVE);
        return r;
    }

    private ContentRuleService contentRuleService() {
        return new ContentRuleService(contentRuleRepository, contentRuleVersionService, objectMapper);
    }

    @Test
    void contentRule_getById_crossProject_sameTenant_returnsEmpty() {
        TenantContext.setTenantId("tenant-a");
        TenantContext.setProjectId("project-a");
        when(contentRuleRepository.findById("content-rule-victim"))
                .thenReturn(Optional.of(contentRuleOwnedBy("tenant-a", "project-B")));

        assertThat(contentRuleService().getById("content-rule-victim")).isEmpty();
    }

    @Test
    void contentRule_getById_crossTenant_returnsEmpty() {
        TenantContext.setTenantId("tenant-a");
        TenantContext.setProjectId("project-a");
        when(contentRuleRepository.findById("content-rule-victim"))
                .thenReturn(Optional.of(contentRuleOwnedBy("tenant-VICTIM", "project-a")));

        assertThat(contentRuleService().getById("content-rule-victim")).isEmpty();
    }

    @Test
    void contentRule_deleteRule_crossProject_doesNotSoftDelete() {
        TenantContext.setTenantId("tenant-a");
        TenantContext.setProjectId("project-a");
        when(contentRuleRepository.findById("content-rule-victim"))
                .thenReturn(Optional.of(contentRuleOwnedBy("tenant-a", "project-B")));

        contentRuleService().deleteRule("content-rule-victim");

        verify(contentRuleRepository, never()).save(any());
    }
}
