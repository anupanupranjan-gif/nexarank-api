// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.model;

import com.nexarank.api.compliance.Pii;
import com.nexarank.api.compliance.PiiCategory;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "zero_result_queries")
public class ZeroResultQuery {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(nullable = false)
    private String query;

    @Pii(PiiCategory.PSEUDONYMOUS_ID)
    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "occurred_at")
    private Instant occurredAt = Instant.now();

    /** NR-59: the LLM-suggested alternative query, if a recovery attempt was made. */
    @Column(name = "suggested_query")
    private String suggestedQuery;

    /** NR-59: whether retrying with suggestedQuery actually returned results. */
    @Column(nullable = false)
    private boolean recovered = false;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public String getSuggestedQuery() { return suggestedQuery; }
    public void setSuggestedQuery(String suggestedQuery) { this.suggestedQuery = suggestedQuery; }
    public boolean isRecovered() { return recovered; }
    public void setRecovered(boolean recovered) { this.recovered = recovered; }
}
