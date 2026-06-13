// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "stopword_list",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "project_id", "word"}))
public class Stopword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(nullable = false)
    private String word;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    public Long getId()                        { return id; }
    public void setId(Long id)                 { this.id = id; }
    public String getTenantId()                { return tenantId; }
    public void setTenantId(String tenantId)   { this.tenantId = tenantId; }
    public String getProjectId()               { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getWord()                    { return word; }
    public void setWord(String word)           { this.word = word; }
    public String getCreatedBy()               { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt()              { return createdAt; }
    public void setCreatedAt(Instant createdAt){ this.createdAt = createdAt; }
}
