// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "merch_rules")
public class MerchRule {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleType type;

    @Column(nullable = false)
    private String query;

    @Column(name = "boost_field")
    private String boostField;

    @Column(name = "boost_value")
    private String boostValue;

    @Column(name = "boost_factor")
    private Float boostFactor;

    @Column(name = "pinned_ids", columnDefinition = "TEXT")
    private String pinnedIdsJson;

    @Column(name = "synonyms", columnDefinition = "TEXT")
    private String synonymsJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleStatus status = RuleStatus.DRAFT;

    @Column(nullable = false)
    private int priority = 50;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "require_query", nullable = false)
    private boolean requireQuery = true;


    @Column(name = "submitted_by")
    private String submittedBy;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "rejection_comment")
    private String rejectionComment;

    @Column(name = "activate_at")
    private Instant activateAt;

    @Column(name = "expire_at")
    private Instant expireAt;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    @Transient
    private List<String> pinnedIds;

    @Transient
    private List<String> synonyms;

    public enum RuleType { BOOST, PIN, BURY, SYNONYM }

    public enum RuleStatus { DRAFT, PENDING_REVIEW, APPROVED, REJECTED, DISABLED }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public RuleType getType() { return type; }
    public void setType(RuleType type) { this.type = type; }
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public String getBoostField() { return boostField; }
    public void setBoostField(String boostField) { this.boostField = boostField; }
    public String getBoostValue() { return boostValue; }
    public void setBoostValue(String boostValue) { this.boostValue = boostValue; }
    public Float getBoostFactor() { return boostFactor; }
    public void setBoostFactor(Float boostFactor) { this.boostFactor = boostFactor; }
    public String getPinnedIdsJson() { return pinnedIdsJson; }
    public void setPinnedIdsJson(String pinnedIdsJson) { this.pinnedIdsJson = pinnedIdsJson; }
    public String getSynonymsJson() { return synonymsJson; }
    public void setSynonymsJson(String synonymsJson) { this.synonymsJson = synonymsJson; }
    public RuleStatus getStatus() { return status; }
    public void setStatus(RuleStatus status) { this.status = status; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public String getRejectionComment() { return rejectionComment; }
    public void setRejectionComment(String rejectionComment) { this.rejectionComment = rejectionComment; }
    public void setReviewComment(String comment) { this.rejectionComment = comment; }
    public String getReviewComment() { return rejectionComment; }
    public Instant getActivateAt() { return activateAt; }
    public void setActivateAt(Instant activateAt) { this.activateAt = activateAt; }
    public Instant getExpireAt() { return expireAt; }
    public void setExpireAt(Instant expireAt) { this.expireAt = expireAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public List<String> getPinnedIds() { return pinnedIds; }
    public void setPinnedIds(List<String> pinnedIds) { this.pinnedIds = pinnedIds; }
    public List<String> getSynonyms() { return synonyms; }
    public void setSynonyms(List<String> synonyms) { this.synonyms = synonyms; }
    public boolean isRequireQuery() { return requireQuery; }
    public void setRequireQuery(boolean requireQuery) { this.requireQuery = requireQuery; }

    @Transient
    private java.util.List<com.nexarank.api.model.RuleTriggerCondition> triggerConditions;

    public java.util.List<com.nexarank.api.model.RuleTriggerCondition> getTriggerConditions() {
        return triggerConditions;
    }
    public void setTriggerConditions(
            java.util.List<com.nexarank.api.model.RuleTriggerCondition> triggerConditions) {
        this.triggerConditions = triggerConditions;
    }
}
