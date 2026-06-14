// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "watched_queries")
public class WatchedQuery {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(nullable = false)
    private String query;

    @Column(name = "expected_min_ctr")
    private Double expectedMinCtr;

    @Column(name = "expected_max_position")
    private Double expectedMaxPosition;

    @Column
    private String notes;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public String getId()                              { return id; }
    public void setId(String id)                       { this.id = id; }
    public String getTenantId()                        { return tenantId; }
    public void setTenantId(String t)                  { this.tenantId = t; }
    public String getProjectId()                       { return projectId; }
    public void setProjectId(String p)                 { this.projectId = p; }
    public String getQuery()                           { return query; }
    public void setQuery(String q)                     { this.query = q; }
    public Double getExpectedMinCtr()                  { return expectedMinCtr; }
    public void setExpectedMinCtr(Double v)            { this.expectedMinCtr = v; }
    public Double getExpectedMaxPosition()             { return expectedMaxPosition; }
    public void setExpectedMaxPosition(Double v)       { this.expectedMaxPosition = v; }
    public String getNotes()                           { return notes; }
    public void setNotes(String n)                     { this.notes = n; }
    public boolean isEnabled()                         { return enabled; }
    public void setEnabled(boolean e)                  { this.enabled = e; }
    public String getCreatedBy()                       { return createdBy; }
    public void setCreatedBy(String c)                 { this.createdBy = c; }
    public Instant getCreatedAt()                      { return createdAt; }
    public void setCreatedAt(Instant t)                { this.createdAt = t; }
    public Instant getUpdatedAt()                      { return updatedAt; }
    public void setUpdatedAt(Instant t)                { this.updatedAt = t; }
}
