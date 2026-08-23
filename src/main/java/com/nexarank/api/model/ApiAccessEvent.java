// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.model;

import com.nexarank.api.compliance.Pii;
import com.nexarank.api.compliance.PiiCategory;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * NR-70 Tier 2: API access log and failed authentication attempts.
 *
 * Kept separate from audit_events because this is request-rate volume with a
 * different shape (endpoint/status/latency) and different retention pressure —
 * folding it in would swamp the rule-change history it sits alongside.
 *
 * Append-only: nothing updates or deletes these rows except the scheduled
 * retention purge (AuditRetentionService).
 */
@Entity
@Table(name = "api_access_events")
public class ApiAccessEvent {

    /** API_ACCESS = a served request; AUTH_FAILURE = a rejected login attempt. */
    public enum EventType { API_ACCESS, AUTH_FAILURE }

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "project_id")
    private String projectId;

    @Column(name = "user_id")
    private String userId;

    @Pii(PiiCategory.DIRECT_IDENTIFIER)
    @Column(name = "username")
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType = EventType.API_ACCESS;

    @Column(nullable = false)
    private String endpoint;

    @Column(name = "http_method")
    private String httpMethod;

    @Pii(value = PiiCategory.OTHER_SENSITIVE, note = "known secret param names are redacted by ApiAccessLogFilter before persisting, but arbitrary request params otherwise pass through unfiltered")
    @Column(columnDefinition = "TEXT")
    private String params;

    @Column(name = "response_code")
    private Integer responseCode;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Pii(PiiCategory.IP_ADDRESS)
    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

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
    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }
    public String getParams() { return params; }
    public void setParams(String params) { this.params = params; }
    public Integer getResponseCode() { return responseCode; }
    public void setResponseCode(Integer responseCode) { this.responseCode = responseCode; }
    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
