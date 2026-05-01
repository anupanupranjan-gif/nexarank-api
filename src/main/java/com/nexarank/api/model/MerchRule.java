package com.nexarank.api.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;
import java.util.List;

@Document(indexName = "merch_rules")
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

    @Field(type = FieldType.Date)
    private Instant createdAt;

    @Field(type = FieldType.Date)
    private Instant updatedAt;

    public enum RuleType {
        PIN, BOOST, BURY, SYNONYM
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
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
