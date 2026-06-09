// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "quality_eval_results")
public class QualityEvalResult {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "run_at")
    private Instant runAt = Instant.now();

    @Column(name = "ndcg_at_5")
    private Double ndcgAt5;

    @Column(name = "ndcg_at_10")
    private Double ndcgAt10;

    @Column(name = "mrr_at_10")
    private Double mrrAt10;

    @Column(name = "queries_evaluated")
    private Integer queriesEvaluated;

    @Column
    private String notes;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public Instant getRunAt() { return runAt; }
    public void setRunAt(Instant runAt) { this.runAt = runAt; }
    public Double getNdcgAt5() { return ndcgAt5; }
    public void setNdcgAt5(Double ndcgAt5) { this.ndcgAt5 = ndcgAt5; }
    public Double getNdcgAt10() { return ndcgAt10; }
    public void setNdcgAt10(Double ndcgAt10) { this.ndcgAt10 = ndcgAt10; }
    public Double getMrrAt10() { return mrrAt10; }
    public void setMrrAt10(Double mrrAt10) { this.mrrAt10 = mrrAt10; }
    public Integer getQueriesEvaluated() { return queriesEvaluated; }
    public void setQueriesEvaluated(Integer queriesEvaluated) { this.queriesEvaluated = queriesEvaluated; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
