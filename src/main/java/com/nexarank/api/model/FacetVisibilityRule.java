// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Phase 24 / NR-38: Conditional facet visibility rule.
 *
 * When trigger_facet_field=trigger_facet_value is selected:
 * - show_facets: fieldNames to make visible (in addition to always-visible ones)
 * - hide_facets: fieldNames to hide
 *
 * Higher priority wins if two rules conflict.
 */
@Entity
@Table(name = "facet_visibility_rules")
public class FacetVisibilityRule {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(nullable = false)
    private String name;

    @Column(name = "trigger_facet_field", nullable = false)
    private String triggerFacetField;

    @Column(name = "trigger_facet_value", nullable = false)
    private String triggerFacetValue;

    /** JSON array of fieldNames to show when trigger matches */
    @Column(name = "show_facets", nullable = false, columnDefinition = "TEXT")
    private String showFacetsJson = "[]";

    /** JSON array of fieldNames to hide when trigger matches */
    @Column(name = "hide_facets", nullable = false, columnDefinition = "TEXT")
    private String hideFacetsJson = "[]";

    @Column(nullable = false)
    private int priority = 50;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Transient
    private java.util.List<String> showFacets;

    @Transient
    private java.util.List<String> hideFacets;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTriggerFacetField() { return triggerFacetField; }
    public void setTriggerFacetField(String triggerFacetField) { this.triggerFacetField = triggerFacetField; }
    public String getTriggerFacetValue() { return triggerFacetValue; }
    public void setTriggerFacetValue(String triggerFacetValue) { this.triggerFacetValue = triggerFacetValue; }
    public String getShowFacetsJson() { return showFacetsJson; }
    public void setShowFacetsJson(String showFacetsJson) { this.showFacetsJson = showFacetsJson; }
    public String getHideFacetsJson() { return hideFacetsJson; }
    public void setHideFacetsJson(String hideFacetsJson) { this.hideFacetsJson = hideFacetsJson; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public java.util.List<String> getShowFacets() { return showFacets; }
    public void setShowFacets(java.util.List<String> showFacets) { this.showFacets = showFacets; }
    public java.util.List<String> getHideFacets() { return hideFacets; }
    public void setHideFacets(java.util.List<String> hideFacets) { this.hideFacets = hideFacets; }
}
