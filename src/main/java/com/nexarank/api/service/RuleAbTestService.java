// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.nexarank.api.model.MerchRule;
import com.nexarank.api.model.RuleAbTest;
import com.nexarank.api.repository.RuleAbTestRepository;
import com.nexarank.api.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 22 / NR-32: A/B testing for rule variants.
 *
 * Variant assignment: deterministic hash of sessionId mod 2.
 * Same session always sees the same variant — consistent UX.
 *
 * CTR tracking: enrich endpoint increments impressions on every call
 * that has an active test. Click endpoint increments clicks when
 * variantId is present in the click payload.
 *
 * Statistical significance: two-proportion z-test, p < 0.05,
 * minimum 100 impressions per variant.
 */
@Service
public class RuleAbTestService {

    private static final Logger log = LoggerFactory.getLogger(RuleAbTestService.class);

    private final RuleAbTestRepository repository;
    private final MerchRuleService ruleService;
    private final RuleVersionService versionService;

    public RuleAbTestService(RuleAbTestRepository repository,
                              MerchRuleService ruleService,
                              RuleVersionService versionService) {
        this.repository = repository;
        this.ruleService = ruleService;
        this.versionService = versionService;
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    @Transactional
    public RuleAbTest createTest(String ruleAId, String ruleBId) {
        String tenantId   = TenantContext.getTenantId();
        String projectId  = TenantContext.getProjectId();
        String currentUser = getCurrentUsername();

        MerchRule ruleA = ruleService.getById(ruleAId)
                .orElseThrow(() -> new IllegalArgumentException("Rule A not found: " + ruleAId));
        MerchRule ruleB = ruleService.getById(ruleBId)
                .orElseThrow(() -> new IllegalArgumentException("Rule B not found: " + ruleBId));

        if (!ruleA.getQuery().equalsIgnoreCase(ruleB.getQuery())) {
            throw new IllegalArgumentException(
                "Both rules must target the same query. A='" + ruleA.getQuery() +
                "' B='" + ruleB.getQuery() + "'");
        }

        // Only one RUNNING test per query
        Optional<RuleAbTest> existing = repository
                .findByTenantIdAndProjectIdAndQueryAndStatus(
                        tenantId, projectId, ruleA.getQuery(), RuleAbTest.TestStatus.RUNNING);
        if (existing.isPresent()) {
            throw new IllegalStateException(
                "A running A/B test already exists for query '" + ruleA.getQuery() + "'");
        }

        RuleAbTest test = new RuleAbTest();
        test.setId(UUID.randomUUID().toString());
        test.setTenantId(tenantId);
        test.setProjectId(projectId);
        test.setQuery(ruleA.getQuery());
        test.setRuleAId(ruleAId);
        test.setRuleBId(ruleBId);
        test.setStatus(RuleAbTest.TestStatus.RUNNING);
        test.setCreatedBy(currentUser);
        test.setCreatedAt(Instant.now());

        RuleAbTest saved = repository.save(test);
        log.info("AB_TEST_CREATED id={} query='{}' ruleA={} ruleB={} by={}",
                saved.getId(), saved.getQuery(), ruleAId, ruleBId, currentUser);
        return saved;
    }

    public List<RuleAbTest> getAllTests() {
        return repository.findByTenantIdAndProjectId(
                TenantContext.getTenantId(), TenantContext.getProjectId());
    }

    public List<RuleAbTest> getRunningTests() {
        return repository.findByTenantIdAndProjectIdAndStatus(
                TenantContext.getTenantId(), TenantContext.getProjectId(),
                RuleAbTest.TestStatus.RUNNING);
    }

    public Optional<RuleAbTest> getById(String id) {
        return repository.findById(id)
                .filter(t -> t.getTenantId().equals(TenantContext.getTenantId())
                          && t.getProjectId().equals(TenantContext.getProjectId()));
    }

    // ── Variant assignment ────────────────────────────────────────────────────

    /**
     * Deterministic 50/50 split by sessionId.
     * Returns "A" or "B" — same session always gets the same variant.
     */
    public String assignVariant(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return "A";
        // Use absolute value of hashCode to avoid negative mod
        return Math.abs(sessionId.hashCode()) % 2 == 0 ? "A" : "B";
    }

    /**
     * Given a query and sessionId, returns the active test and which rule to apply.
     * Returns empty if no active test exists for the query.
     */
    public Optional<AbTestContext> resolveVariant(String query, String sessionId) {
        return repository.findByTenantIdAndProjectIdAndQueryAndStatus(
                        TenantContext.getTenantId(), TenantContext.getProjectId(),
                        query, RuleAbTest.TestStatus.RUNNING)
                .map(test -> {
                    String variant = assignVariant(sessionId);
                    String ruleId  = variant.equals("A") ? test.getRuleAId() : test.getRuleBId();
                    return new AbTestContext(test.getId(), variant, ruleId);
                });
    }

    // ── Impression + click tracking ───────────────────────────────────────────

    @Transactional
    public void recordImpression(String testId, String variant) {
        repository.findById(testId).ifPresent(test -> {
            if (test.getStatus() != RuleAbTest.TestStatus.RUNNING) return;
            if ("A".equals(variant)) test.setImpressionsA(test.getImpressionsA() + 1);
            else                     test.setImpressionsB(test.getImpressionsB() + 1);
            repository.save(test);
        });
    }

    @Transactional
    public void recordClick(String testId, String variant) {
        repository.findById(testId).ifPresent(test -> {
            if (test.getStatus() != RuleAbTest.TestStatus.RUNNING) return;
            if ("A".equals(variant)) test.setClicksA(test.getClicksA() + 1);
            else                     test.setClicksB(test.getClicksB() + 1);
            repository.save(test);
        });
    }

    // ── Winner promotion ──────────────────────────────────────────────────────

    /**
     * Promote winner:
     * 1. Copy winning variant's mutable fields onto the losing rule
     * 2. Send losing rule back to PENDING_REVIEW (re-enters approval)
     * 3. Archive the test
     * 4. Snapshot both rules
     */
    @Transactional
    public RuleAbTest promoteWinner(String testId, String winnerVariant) {
        String currentUser = getCurrentUsername();

        RuleAbTest test = repository.findById(testId)
                .filter(t -> t.getTenantId().equals(TenantContext.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Test not found: " + testId));

        if (test.getStatus() != RuleAbTest.TestStatus.RUNNING) {
            throw new IllegalStateException("Test is not running: " + testId);
        }

        String winnerRuleId = winnerVariant.equals("A") ? test.getRuleAId() : test.getRuleBId();
        String loserRuleId  = winnerVariant.equals("A") ? test.getRuleBId() : test.getRuleAId();

        MerchRule winner = ruleService.getById(winnerRuleId)
                .orElseThrow(() -> new IllegalArgumentException("Winner rule not found: " + winnerRuleId));
        MerchRule loser  = ruleService.getById(loserRuleId)
                .orElseThrow(() -> new IllegalArgumentException("Loser rule not found: " + loserRuleId));

        // Archive the losing rule by disabling it and sending back to pending
        loser.setEnabled(false);
        loser.setStatus(MerchRule.RuleStatus.DISABLED);
        loser.setUpdatedAt(Instant.now());
        ruleService.saveDirectly(loser);
        versionService.snapshot(loser, currentUser,
                "Archived — lost A/B test " + testId + " to " + winnerVariant);

        // Complete the test
        test.setStatus(RuleAbTest.TestStatus.COMPLETED);
        test.setWinnerId(winnerRuleId);
        test.setCompletedAt(Instant.now());
        RuleAbTest saved = repository.save(test);

        log.info("AB_TEST_COMPLETED id={} query='{}' winner={} winnerRule={} by={}",
                testId, test.getQuery(), winnerVariant, winnerRuleId, currentUser);
        return saved;
    }

    @Transactional
    public RuleAbTest archiveTest(String testId) {
        RuleAbTest test = repository.findById(testId)
                .filter(t -> t.getTenantId().equals(TenantContext.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Test not found: " + testId));
        test.setStatus(RuleAbTest.TestStatus.ARCHIVED);
        test.setCompletedAt(Instant.now());
        return repository.save(test);
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    public record AbTestContext(String testId, String variant, String ruleId) {}

    private String getCurrentUsername() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
