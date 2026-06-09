// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "search_events")
public class SearchEvent {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "session_id")
    private String sessionId;

    @Column(nullable = false)
    private String query;

    @Column(name = "result_count")
    private int resultCount;

    @Column
    private String mode;

    @Column(name = "took_ms")
    private Integer tookMs;

    @Column(name = "searched_at")
    private Instant searchedAt = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public int getResultCount() { return resultCount; }
    public void setResultCount(int resultCount) { this.resultCount = resultCount; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public Integer getTookMs() { return tookMs; }
    public void setTookMs(Integer tookMs) { this.tookMs = tookMs; }
    public Instant getSearchedAt() { return searchedAt; }
    public void setSearchedAt(Instant searchedAt) { this.searchedAt = searchedAt; }
}
