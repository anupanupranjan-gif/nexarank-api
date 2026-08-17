// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.nexarank.api.model.ApiAccessEvent;
import com.nexarank.api.repository.ApiAccessEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * NR-70 Tier 2: writes the API access log and failed-authentication records.
 *
 * Never throws — an audit write must not be able to fail the request it records.
 */
@Service
public class ApiAccessLogService {

    private static final Logger log = LoggerFactory.getLogger(ApiAccessLogService.class);

    /**
     * Query params whose values must never be persisted. The audit log records
     * that a call happened and with which parameter names, not the secrets that
     * were passed — writing these down would turn the audit table itself into a
     * credential store.
     */
    private static final Set<String> REDACTED_PARAMS = Set.of(
            "password", "currentpassword", "newpassword", "token", "apikey",
            "api_key", "secret", "authorization", "refreshtoken");

    private static final int MAX_PARAMS_LENGTH = 1000;

    private final ApiAccessEventRepository repository;

    public ApiAccessLogService(ApiAccessEventRepository repository) {
        this.repository = repository;
    }

    public void recordAccess(String tenantId, String projectId, String username,
                             String endpoint, String method, Map<String, String[]> params,
                             int responseCode, long latencyMs, String ipAddress) {
        try {
            ApiAccessEvent event = new ApiAccessEvent();
            event.setId(UUID.randomUUID().toString());
            event.setTenantId(tenantId);
            event.setProjectId(projectId);
            event.setUsername(username);
            event.setEventType(ApiAccessEvent.EventType.API_ACCESS);
            event.setEndpoint(endpoint);
            event.setHttpMethod(method);
            event.setParams(formatParams(params));
            event.setResponseCode(responseCode);
            event.setLatencyMs(latencyMs);
            event.setIpAddress(ipAddress);
            event.setCreatedAt(Instant.now());
            repository.save(event);
        } catch (Exception e) {
            log.warn("Failed to record API access event: {}", e.getMessage());
        }
    }

    /**
     * A rejected login. Deliberately records the attempted username and source
     * IP but never the submitted password, and is written even when the username
     * doesn't exist — repeated failures against unknown accounts are exactly the
     * enumeration signal this log exists to surface.
     */
    public void recordAuthFailure(String tenantId, String attemptedUsername,
                                  String endpoint, String ipAddress, String detail) {
        try {
            ApiAccessEvent event = new ApiAccessEvent();
            event.setId(UUID.randomUUID().toString());
            event.setTenantId(tenantId != null ? tenantId : "unknown");
            event.setUsername(attemptedUsername);
            event.setEventType(ApiAccessEvent.EventType.AUTH_FAILURE);
            event.setEndpoint(endpoint);
            event.setHttpMethod("POST");
            event.setParams(detail);
            event.setResponseCode(401);
            event.setIpAddress(ipAddress);
            event.setCreatedAt(Instant.now());
            repository.save(event);
            log.info("AUDIT action=AUTH_FAILURE user={} ip={} tenant={}",
                    attemptedUsername, ipAddress, event.getTenantId());
        } catch (Exception e) {
            log.warn("Failed to record auth failure event: {}", e.getMessage());
        }
    }

    /** Real client IP, honouring the proxy header the ingress sets. */
    public String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String formatParams(Map<String, String[]> params) {
        if (params == null || params.isEmpty()) return null;
        String formatted = params.entrySet().stream()
                .map(e -> {
                    String key = e.getKey();
                    if (REDACTED_PARAMS.contains(key.toLowerCase())) return key + "=[REDACTED]";
                    String value = e.getValue() == null || e.getValue().length == 0
                            ? "" : String.join(",", e.getValue());
                    return key + "=" + value;
                })
                .collect(Collectors.joining("&"));
        return formatted.length() > MAX_PARAMS_LENGTH
                ? formatted.substring(0, MAX_PARAMS_LENGTH) + "...(truncated)"
                : formatted;
    }
}
