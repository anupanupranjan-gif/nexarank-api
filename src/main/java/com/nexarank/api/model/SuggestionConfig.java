// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Configurable thresholds for AI rule suggestions per tenant/project.
 * Replaces hardcoded values in AiRuleSuggestionService.
 */
@Entity
@Table(name = "suggestion_config")
public class SuggestionConfig {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    /** Minimum CTR — queries below this are boost candidates */
    @Column(name = "min_ctr", nullable = false)
    private double minCtr = 0.05;

    /** Max avg click position — products clicked below this rank are boost candidates */
    @Column(name = "max_click_position", nullable = false)
    private double maxClickPosition = 4.0;

    /** Minimum clicks before suggesting a boost */
    @Column(name = "min_clicks", nullable = false)
    private int minClicks = 1;

    /** Minimum impressions before suggesting (reduces noise) */
    @Column(name = "min_impressions", nullable = false)
    private int minImpressions = 5;

    /** How many days of click history to analyze */
    @Column(name = "lookback_days", nullable = false)
    private int lookbackDays = 30;

    /** Max number of suggestions to return */
    @Column(name = "max_suggestions", nullable = false)
    private int maxSuggestions = 10;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public String getId()                          { return id; }
    public void setId(String id)                   { this.id = id; }
    public String getTenantId()                    { return tenantId; }
    public void setTenantId(String t)              { this.tenantId = t; }
    public String getProjectId()                   { return projectId; }
    public void setProjectId(String p)             { this.projectId = p; }
    public double getMinCtr()                      { return minCtr; }
    public void setMinCtr(double v)                { this.minCtr = v; }
    public double getMaxClickPosition()            { return maxClickPosition; }
    public void setMaxClickPosition(double v)      { this.maxClickPosition = v; }
    public int getMinClicks()                      { return minClicks; }
    public void setMinClicks(int v)                { this.minClicks = v; }
    public int getMinImpressions()                 { return minImpressions; }
    public void setMinImpressions(int v)           { this.minImpressions = v; }
    public int getLookbackDays()                   { return lookbackDays; }
    public void setLookbackDays(int v)             { this.lookbackDays = v; }
    public int getMaxSuggestions()                 { return maxSuggestions; }
    public void setMaxSuggestions(int v)           { this.maxSuggestions = v; }
    public Instant getCreatedAt()                  { return createdAt; }
    public void setCreatedAt(Instant t)            { this.createdAt = t; }
    public Instant getUpdatedAt()                  { return updatedAt; }
    public void setUpdatedAt(Instant t)            { this.updatedAt = t; }
}
