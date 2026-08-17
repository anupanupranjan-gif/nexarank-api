// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.model.ApiAccessEvent;
import com.nexarank.api.model.AuditEvent;
import com.nexarank.api.service.AuditQueryService;
import com.nexarank.api.service.AuditRetentionService;
import com.nexarank.api.service.AuditService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NR-70: two-tier audit log.
 *
 * Tier 1 (/rule-changes) — rule-change history, MERCHANDISER and above,
 * scoped to the caller's assigned project(s) via user_projects.
 * Tier 2 (/full, /api-access, /auth-failures) — ADMIN only; approval reasons,
 * API access patterns, and authentication failures are security-posture data
 * and are not exposed at the merchandiser level.
 *
 * The tier boundary is enforced by distinct endpoints with distinct role
 * matchers in SecurityConfig, not by filtering rows out of one shared response.
 *
 * Append-only: this controller deliberately exposes no POST/PUT/PATCH/DELETE
 * for audit records. The single admin-only mutation is the retention purge,
 * which deletes strictly by age.
 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditService auditService;
    private final AuditQueryService queryService;
    private final AuditRetentionService retentionService;

    public AuditController(AuditService auditService,
                           AuditQueryService queryService,
                           AuditRetentionService retentionService) {
        this.auditService = auditService;
        this.queryService = queryService;
        this.retentionService = retentionService;
    }

    // ── Tier 1: rule-change history ─────────────────────────────────────────

    @GetMapping("/rule-changes")
    public ResponseEntity<?> ruleChanges(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "30") int days) {

        Instant start = resolveStart(startDate, days);
        Instant end = resolveEnd(endDate);
        Page<AuditEvent> result = queryService.tier1(
                currentUsername(), actor, action, start, end, page, size);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", result.getContent().stream().map(queryService::toRuleChangeView).toList());
        body.put("page", result.getNumber());
        body.put("size", result.getSize());
        body.put("totalElements", result.getTotalElements());
        body.put("totalPages", result.getTotalPages());
        body.put("visibleProjects", queryService.visibleProjectIds(currentUsername()));
        body.put("actions", queryService.distinctActions(1));
        return ResponseEntity.ok(body);
    }

    /** Tier 1 export — available to anyone who can view Tier 1. */
    @GetMapping("/rule-changes/export.csv")
    public ResponseEntity<byte[]> exportRuleChanges(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "30") int days) {

        List<AuditEvent> events = queryService.tier1Export(
                currentUsername(), actor, action, resolveStart(startDate, days), resolveEnd(endDate));

        StringBuilder csv = new StringBuilder(
                "Timestamp,Actor,Action,Rule,Rule ID,Project,Previous State,New State,Changes,Reason\n");
        for (AuditEvent e : events) {
            csv.append(csvRow(
                    String.valueOf(e.getCreatedAt()), e.getUsername(), e.getAction(),
                    e.getEntityName(), e.getEntityId(), e.getProjectId(),
                    e.getPreviousState(), e.getNewState(), e.getDetails(), e.getReason()));
        }
        return csvResponse(csv.toString(), "nexarank-rule-change-history.csv");
    }

    // ── Tier 2: full audit log (ADMIN only) ─────────────────────────────────

    @GetMapping("/full")
    public ResponseEntity<?> fullAuditLog(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "30") int days) {

        Page<AuditEvent> result = queryService.tier2(
                actor, action, resolveStart(startDate, days), resolveEnd(endDate), page, size);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", result.getContent());
        body.put("page", result.getNumber());
        body.put("size", result.getSize());
        body.put("totalElements", result.getTotalElements());
        body.put("totalPages", result.getTotalPages());
        body.put("actions", queryService.distinctActions(null));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/full/export.csv")
    public ResponseEntity<byte[]> exportFullAuditLog(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "30") int days) {

        List<AuditEvent> events = queryService.tier2Export(
                actor, action, resolveStart(startDate, days), resolveEnd(endDate));

        StringBuilder csv = new StringBuilder(
                "Timestamp,Tier,Actor,Action,Entity,Entity Name,Entity ID,Project,Previous State,New State,Details,Reason\n");
        for (AuditEvent e : events) {
            csv.append(csvRow(
                    String.valueOf(e.getCreatedAt()), String.valueOf(e.getTier()), e.getUsername(),
                    e.getAction(), e.getEntity(), e.getEntityName(), e.getEntityId(), e.getProjectId(),
                    e.getPreviousState(), e.getNewState(), e.getDetails(), e.getReason()));
        }
        return csvResponse(csv.toString(), "nexarank-full-audit-log.csv");
    }

    /** API access log — tenantId, userId, endpoint, params, response code, latency. */
    @GetMapping("/api-access")
    public ResponseEntity<?> apiAccess(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(queryService.apiAccess(ApiAccessEvent.EventType.API_ACCESS, page, size));
    }

    /** Failed authentication attempts, with IP and timestamp. */
    @GetMapping("/auth-failures")
    public ResponseEntity<?> authFailures(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(queryService.apiAccess(ApiAccessEvent.EventType.AUTH_FAILURE, page, size));
    }

    @GetMapping("/api-access/export.csv")
    public ResponseEntity<byte[]> exportApiAccess(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "30") int days) {

        ApiAccessEvent.EventType eventType = null;
        if (type != null && !type.isBlank()) {
            try {
                eventType = ApiAccessEvent.EventType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown event type: " + type);
            }
        }

        List<ApiAccessEvent> events = queryService.apiAccessExport(
                eventType, username, resolveStart(startDate, days), resolveEnd(endDate));

        StringBuilder csv = new StringBuilder(
                "Timestamp,Type,Tenant,User,Endpoint,Method,Params,Response Code,Latency (ms),IP Address\n");
        for (ApiAccessEvent e : events) {
            csv.append(csvRow(
                    String.valueOf(e.getCreatedAt()), String.valueOf(e.getEventType()), e.getTenantId(),
                    e.getUsername(), e.getEndpoint(), e.getHttpMethod(), e.getParams(),
                    String.valueOf(e.getResponseCode()), String.valueOf(e.getLatencyMs()), e.getIpAddress()));
        }
        return csvResponse(csv.toString(), "nexarank-api-access-log.csv");
    }

    /**
     * Runs the retention purge immediately instead of waiting for the nightly
     * schedule. Deletes strictly by age against each tenant's configured
     * retention — it takes no selector of any kind, so it can't be used to
     * remove a specific inconvenient record.
     */
    @PostMapping("/retention/purge")
    public ResponseEntity<?> runRetentionPurge() {
        return ResponseEntity.ok(retentionService.runPurge());
    }

    /** Legacy endpoint, kept so existing callers don't break. Tier 2, ADMIN only. */
    @GetMapping
    public ResponseEntity<?> getAuditLog(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "false") boolean projectOnly) {
        if (projectOnly) {
            return ResponseEntity.ok(auditService.getAuditLogByProject(page, size));
        }
        return ResponseEntity.ok(auditService.getAuditLog(page, size));
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    private Instant resolveStart(String startDate, int days) {
        if (startDate != null && !startDate.isBlank()) {
            return parseDate(startDate).atStartOfDay(ZoneOffset.UTC).toInstant();
        }
        return queryService.defaultStart(days);
    }

    private Instant resolveEnd(String endDate) {
        if (endDate != null && !endDate.isBlank()) {
            // Inclusive of the end date itself.
            return parseDate(endDate).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        }
        return Instant.now();
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid date (expected YYYY-MM-DD): " + value);
        }
    }

    private String csvRow(String... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(csvEscape(values[i]));
        }
        return sb.append('\n').toString();
    }

    private String csvEscape(String value) {
        if (value == null || "null".equals(value)) return "";
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private ResponseEntity<byte[]> csvResponse(String csv, String filename) {
        byte[] bytes = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(bytes.length);
        return new ResponseEntity<>(bytes, headers, org.springframework.http.HttpStatus.OK);
    }
}
