// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "facet_config")
public class FacetConfig {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "field_name", nullable = false)
    private String fieldName;

    @Column(name = "display_label")
    private String displayLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "facet_type", nullable = false)
    private FacetType facetType;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "show_count")
    private boolean showCount = true;

    @Column(name = "sort_order")
    private int sortOrder = 0;

    @Column(name = "max_values")
    private Integer maxValues;

    @Column(name = "range_min")
    private Double rangeMin;

    @Column(name = "range_max")
    private Double rangeMax;

    @Column(name = "range_interval")
    private Double rangeInterval;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public enum FacetType { TERMS, RANGE, BOOLEAN }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public String getDisplayLabel() { return displayLabel; }
    public void setDisplayLabel(String displayLabel) { this.displayLabel = displayLabel; }
    public FacetType getFacetType() { return facetType; }
    public void setFacetType(FacetType facetType) { this.facetType = facetType; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isShowCount() { return showCount; }
    public void setShowCount(boolean showCount) { this.showCount = showCount; }
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
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
