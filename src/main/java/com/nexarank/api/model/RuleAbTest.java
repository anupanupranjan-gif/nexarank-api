// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "rule_ab_tests")
public class RuleAbTest {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(nullable = false)
    private String query;

    @Column(name = "rule_a_id", nullable = false)
    private String ruleAId;

    @Column(name = "rule_b_id", nullable = false)
    private String ruleBId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TestStatus status = TestStatus.RUNNING;

    @Column(name = "impressions_a", nullable = false)
    private long impressionsA = 0;

    @Column(name = "impressions_b", nullable = false)
    private long impressionsB = 0;

    @Column(name = "clicks_a", nullable = false)
    private long clicksA = 0;

    @Column(name = "clicks_b", nullable = false)
    private long clicksB = 0;

    @Column(name = "winner_id")
    private String winnerId;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    public enum TestStatus { RUNNING, COMPLETED, ARCHIVED }

    // ── Computed helpers ──────────────────────────────────────────────────────

    public double getCtrA() {
        return impressionsA == 0 ? 0.0 : (double) clicksA / impressionsA;
    }

    public double getCtrB() {
        return impressionsB == 0 ? 0.0 : (double) clicksB / impressionsB;
    }

    /**
     * Two-proportion z-test. Returns true when p < 0.05 with >= 100 impressions per variant.
     */
    public boolean isSignificant() {
        if (impressionsA < 100 || impressionsB < 100) return false;
        double ctrA = getCtrA();
        double ctrB = getCtrB();
        double pooled = (double)(clicksA + clicksB) / (impressionsA + impressionsB);
        if (pooled == 0 || pooled == 1) return false;
        double se = Math.sqrt(pooled * (1 - pooled) * (1.0 / impressionsA + 1.0 / impressionsB));
        if (se == 0) return false;
        double z = Math.abs(ctrA - ctrB) / se;
        return z > 1.96; // p < 0.05
    }

    public String getLeadingVariant() {
        if (getCtrA() >= getCtrB()) return "A";
        return "B";
    }

    // ── Getters / setters ─────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public String getRuleAId() { return ruleAId; }
    public void setRuleAId(String ruleAId) { this.ruleAId = ruleAId; }
    public String getRuleBId() { return ruleBId; }
    public void setRuleBId(String ruleBId) { this.ruleBId = ruleBId; }
    public TestStatus getStatus() { return status; }
    public void setStatus(TestStatus status) { this.status = status; }
    public long getImpressionsA() { return impressionsA; }
    public void setImpressionsA(long impressionsA) { this.impressionsA = impressionsA; }
    public long getImpressionsB() { return impressionsB; }
    public void setImpressionsB(long impressionsB) { this.impressionsB = impressionsB; }
    public long getClicksA() { return clicksA; }
    public void setClicksA(long clicksA) { this.clicksA = clicksA; }
    public long getClicksB() { return clicksB; }
    public void setClicksB(long clicksB) { this.clicksB = clicksB; }
    public String getWinnerId() { return winnerId; }
    public void setWinnerId(String winnerId) { this.winnerId = winnerId; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
