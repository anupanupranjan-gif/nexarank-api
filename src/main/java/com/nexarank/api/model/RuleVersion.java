// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Immutable snapshot of a MerchRule at a point in time.
 * One row is appended on every create/update/approve/reject/rollback.
 * Rows are never updated or deleted — history is the audit trail.
 */
@Entity
@Table(name = "rule_versions",
       uniqueConstraints = @UniqueConstraint(name = "uq_rule_version", columnNames = {"rule_id", "version_number"}))
public class RuleVersion {

    @Id
    private String id;

    @Column(name = "rule_id", nullable = false)
    private String ruleId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    /** Full rule state as JSON at this version. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String snapshot;

    @Column(name = "changed_by")
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt = Instant.now();

    @Column(name = "change_note")
    private String changeNote;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public int getVersionNumber() { return versionNumber; }
    public void setVersionNumber(int versionNumber) { this.versionNumber = versionNumber; }
    public String getSnapshot() { return snapshot; }
    public void setSnapshot(String snapshot) { this.snapshot = snapshot; }
    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }
    public Instant getChangedAt() { return changedAt; }
    public void setChangedAt(Instant changedAt) { this.changedAt = changedAt; }
    public String getChangeNote() { return changeNote; }
    public void setChangeNote(String changeNote) { this.changeNote = changeNote; }
}
