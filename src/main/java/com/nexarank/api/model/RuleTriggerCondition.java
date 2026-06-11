// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.model;

import jakarta.persistence.*;

/**
 * One facet condition on a merchandising rule.
 * Multiple conditions per rule = AND logic.
 * facetValuesJson is a JSON array — multiple values = OR logic within the facet.
 *
 * Example: category IN [Battery, Automotive] AND brand IN [Duracell]
 * → two RuleTriggerCondition rows for the rule.
 */
@Entity
@Table(name = "rule_trigger_conditions")
public class RuleTriggerCondition {

    @Id
    private String id;

    @Column(name = "rule_id", nullable = false)
    private String ruleId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "facet_field", nullable = false)
    private String facetField;

    /** JSON array of selected values e.g. ["Battery","Automotive"] */
    @Column(name = "facet_values", nullable = false, columnDefinition = "TEXT")
    private String facetValuesJson;

    @Column(name = "position", nullable = false)
    private int position = 0;

    @Transient
    private java.util.List<String> facetValues;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getFacetField() { return facetField; }
    public void setFacetField(String facetField) { this.facetField = facetField; }
    public String getFacetValuesJson() { return facetValuesJson; }
    public void setFacetValuesJson(String facetValuesJson) { this.facetValuesJson = facetValuesJson; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public java.util.List<String> getFacetValues() { return facetValues; }
    public void setFacetValues(java.util.List<String> facetValues) { this.facetValues = facetValues; }
}
