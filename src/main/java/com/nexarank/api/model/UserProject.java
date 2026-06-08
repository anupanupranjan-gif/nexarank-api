// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.model;

import jakarta.persistence.*;

@Entity
@Table(name = "user_projects")
public class UserProject {

    @Id
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private User.Role role;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public User.Role getRole() { return role; }
    public void setRole(User.Role role) { this.role = role; }
}
