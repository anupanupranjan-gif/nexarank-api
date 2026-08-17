// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "project_id")
    private String projectId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "username")
    private String username;

    @Column(nullable = false)
    private String action;

    @Column
    private String entity;

    @Column(name = "entity_id")
    private String entityId;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    /**
     * NR-70: 1 = rule-change history (MERCHANDISER and above, project-scoped),
     * 2 = full audit log (ADMIN only — security-posture data). Defaults to 2
     * so any new audit action is private until deliberately classified Tier 1.
     */
    @Column(name = "tier", nullable = false)
    private int tier = 2;

    /**
     * Denormalized at write time so an entry stays readable ("BOOST battery")
     * without a read-time join, and survives deletion of the referenced entity.
     */
    @Column(name = "entity_name")
    private String entityName;

    @Column(name = "previous_state")
    private String previousState;

    @Column(name = "new_state")
    private String newState;

    /** JSON array of {field, oldValue, newValue} — see AuditDiffService. */
    @Column(name = "field_diff", columnDefinition = "TEXT")
    private String fieldDiff;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getEntity() { return entity; }
    public void setEntity(String entity) { this.entity = entity; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public int getTier() { return tier; }
    public void setTier(int tier) { this.tier = tier; }
    public String getEntityName() { return entityName; }
    public void setEntityName(String entityName) { this.entityName = entityName; }
    public String getPreviousState() { return previousState; }
    public void setPreviousState(String previousState) { this.previousState = previousState; }
    public String getNewState() { return newState; }
    public void setNewState(String newState) { this.newState = newState; }
    public String getFieldDiff() { return fieldDiff; }
    public void setFieldDiff(String fieldDiff) { this.fieldDiff = fieldDiff; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
