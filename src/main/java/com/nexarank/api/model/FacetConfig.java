// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.time.Instant;

@Document(indexName = "nexarank_facets")
@Setting(replicas = 0)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FacetConfig {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String fieldName;

    @Field(type = FieldType.Keyword)
    private String displayLabel;

    @Field(type = FieldType.Keyword)
    private FacetType facetType;

    @Field(type = FieldType.Boolean)
    private boolean enabled;

    @Field(type = FieldType.Integer)
    private int sortOrder;

    @Field(type = FieldType.Integer)
    private Integer maxValues;

    @Field(type = FieldType.Double)
    private Double rangeMin;

    @Field(type = FieldType.Double)
    private Double rangeMax;

    @Field(type = FieldType.Double)
    private Double rangeInterval;

    @Field(type = FieldType.Boolean)
    private boolean showCount;

    @Field(type = FieldType.Date)
    private Instant createdAt;

    @Field(type = FieldType.Date)
    private Instant updatedAt;

    public enum FacetType {
        TERMS, RANGE, BOOLEAN
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public String getDisplayLabel() { return displayLabel; }
    public void setDisplayLabel(String displayLabel) { this.displayLabel = displayLabel; }
    public FacetType getFacetType() { return facetType; }
    public void setFacetType(FacetType facetType) { this.facetType = facetType; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public Integer getMaxValues() { return maxValues; }
    public void setMaxValues(Integer maxValues) { this.maxValues = maxValues; }
    public Double getRangeMin() { return rangeMin; }
    public void setRangeMin(Double rangeMin) { this.rangeMin = rangeMin; }
    public Double getRangeMax() { return rangeMax; }
    public void setRangeMax(Double rangeMax) { this.rangeMax = rangeMax; }
    public Double getRangeInterval() { return rangeInterval; }
    public void setRangeInterval(Double rangeInterval) { this.rangeInterval = rangeInterval; }
    public boolean isShowCount() { return showCount; }
    public void setShowCount(boolean showCount) { this.showCount = showCount; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
