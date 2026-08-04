// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "judgments")
public class Judgment {

    @Id
    private String id;

    @Column(name = "set_id", nullable = false)
    private String setId;

    @Column(nullable = false)
    private String query;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "product_title")
    private String productTitle;

    @Column(nullable = false)
    private int grade = 0;

    @Column(name = "judged_by")
    private String judgedBy;

    @Column(name = "judged_at")
    private Instant judgedAt = Instant.now();

    /** NR-58: who authored the current grade — HUMAN (manual judging, default) or LLM (auto-scored). */
    @Column(nullable = false)
    private String source = "HUMAN";

    /** NR-58: APPROVED (default — human judgments need no review) or PENDING_REVIEW (fresh LLM output). */
    @Column(nullable = false)
    private String status = "APPROVED";

    /** NR-58: the LLM's original suggested grade, retained even after a human overrides `grade` — needed for agreement-rate tracking. */
    @Column(name = "llm_grade")
    private Integer llmGrade;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSetId() { return setId; }
    public void setSetId(String setId) { this.setId = setId; }
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public String getProductTitle() { return productTitle; }
    public void setProductTitle(String productTitle) { this.productTitle = productTitle; }
    public int getGrade() { return grade; }
    public void setGrade(int grade) { this.grade = grade; }
    public String getJudgedBy() { return judgedBy; }
    public void setJudgedBy(String judgedBy) { this.judgedBy = judgedBy; }
    public Instant getJudgedAt() { return judgedAt; }
    public void setJudgedAt(Instant judgedAt) { this.judgedAt = judgedAt; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getLlmGrade() { return llmGrade; }
    public void setLlmGrade(Integer llmGrade) { this.llmGrade = llmGrade; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
}
