// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "content_rules")
public class ContentRule {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentZone zone;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Column(nullable = false)
    private int priority = 50;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentRuleStatus status = ContentRuleStatus.DRAFT;

    @Column(name = "schedule_start")
    private Instant scheduleStart;

    @Column(name = "schedule_end")
    private Instant scheduleEnd;

    @Column(name = "trigger_conditions", columnDefinition = "TEXT")
    private String triggerConditionsJson;

    @Column(name = "content_payload", columnDefinition = "TEXT")
    private String contentPayloadJson;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    @Column(name = "created_by")
    private String createdBy;

    @Transient
    private List<RuleTriggerCondition> triggerConditions;

    @Transient
    private Map<String, String> contentPayload;

    public enum ContentRuleStatus { DRAFT, ACTIVE, INACTIVE }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public ContentZone getZone() { return zone; }
    public void setZone(ContentZone zone) { this.zone = zone; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public ContentRuleStatus getStatus() { return status; }
    public void setStatus(ContentRuleStatus status) { this.status = status; }
    public Instant getScheduleStart() { return scheduleStart; }
    public void setScheduleStart(Instant scheduleStart) { this.scheduleStart = scheduleStart; }
    public Instant getScheduleEnd() { return scheduleEnd; }
    public void setScheduleEnd(Instant scheduleEnd) { this.scheduleEnd = scheduleEnd; }
    public String getTriggerConditionsJson() { return triggerConditionsJson; }
    public void setTriggerConditionsJson(String triggerConditionsJson) { this.triggerConditionsJson = triggerConditionsJson; }
    public String getContentPayloadJson() { return contentPayloadJson; }
    public void setContentPayloadJson(String contentPayloadJson) { this.contentPayloadJson = contentPayloadJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public List<RuleTriggerCondition> getTriggerConditions() { return triggerConditions; }
    public void setTriggerConditions(List<RuleTriggerCondition> triggerConditions) { this.triggerConditions = triggerConditions; }
    public Map<String, String> getContentPayload() { return contentPayload; }
    public void setContentPayload(Map<String, String> contentPayload) { this.contentPayload = contentPayload; }
}
