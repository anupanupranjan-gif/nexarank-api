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
}
