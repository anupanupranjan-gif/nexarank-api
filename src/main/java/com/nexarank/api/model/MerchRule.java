// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.time.Instant;
import java.util.List;

@Document(indexName = "merch_rules")
@Setting(replicas = 0)
public class MerchRule {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private RuleType type;

    @Field(type = FieldType.Keyword)
    private String query;

    @Field(type = FieldType.Keyword)
    private List<String> pinnedIds;

    @Field(type = FieldType.Keyword)
    private String boostField;

    @Field(type = FieldType.Keyword)
    private String boostValue;

    @Field(type = FieldType.Float)
    private Float boostFactor;

    @Field(type = FieldType.Keyword)
    private List<String> synonyms;

    @Field(type = FieldType.Boolean)
    private boolean enabled;

    @Field(type = FieldType.Keyword)
    private RuleStatus status;

    @Field(type = FieldType.Keyword)
    private String submittedBy;

    @Field(type = FieldType.Keyword)
    private String approvedBy;

    @Field(type = FieldType.Text)
    private String reviewComment;

    @Field(type = FieldType.Date)
    private Instant createdAt;

    @Field(type = FieldType.Date)
    private Instant updatedAt;

    public enum RuleType {
        PIN, BOOST, BURY, SYNONYM
    }

    public enum RuleStatus {
        DRAFT, PENDING_REVIEW, APPROVED, REJECTED, DISABLED
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public RuleType getType() { return type; }
    public void setType(RuleType type) { this.type = type; }
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public List<String> getPinnedIds() { return pinnedIds; }
    public void setPinnedIds(List<String> pinnedIds) { this.pinnedIds = pinnedIds; }
    public String getBoostField() { return boostField; }
    public void setBoostField(String boostField) { this.boostField = boostField; }
    public String getBoostValue() { return boostValue; }
    public void setBoostValue(String boostValue) { this.boostValue = boostValue; }
    public Float getBoostFactor() { return boostFactor; }
    public void setBoostFactor(Float boostFactor) { this.boostFactor = boostFactor; }
    public List<String> getSynonyms() { return synonyms; }
    public void setSynonyms(List<String> synonyms) { this.synonyms = synonyms; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public RuleStatus getStatus() { return status; }
    public void setStatus(RuleStatus status) { this.status = status; }
    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
