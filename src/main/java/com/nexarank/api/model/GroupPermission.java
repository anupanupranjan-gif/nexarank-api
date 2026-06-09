// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "group_permissions")
public class GroupPermission {

    @Id
    private String id;

    @Column(name = "group_id", nullable = false)
    private String groupId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", insertable = false, updatable = false)
    private UserGroup group;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Permission permission;

    public enum Permission {
        RULES_VIEW, RULES_CREATE, RULES_EDIT, RULES_DELETE, RULES_APPROVE,
        FACET_VIEW, FACET_MANAGE,
        ENGINE_CONFIG_VIEW, ENGINE_CONFIG_MANAGE,
        CLICK_INTELLIGENCE_VIEW,
        SEARCH_QUALITY_VIEW, SEARCH_QUALITY_RUN,
        USER_MANAGEMENT,
        AUDIT_LOG_VIEW
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public UserGroup getGroup() { return group; }
    public void setGroup(UserGroup group) { this.group = group; }
    public Permission getPermission() { return permission; }
    public void setPermission(Permission permission) { this.permission = permission; }
}
