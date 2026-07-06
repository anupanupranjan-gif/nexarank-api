// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.nexarank.api.model.AuditEvent;
import com.nexarank.api.repository.AuditEventRepository;
import com.nexarank.api.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    public void log(String action, String entity, String entityId, String details) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "system";
        save(action, entity, entityId, details, username);
    }

    /**
     * NR-68: for transitions the system performs on its own (e.g. tenant
     * auto-publish cascading APPROVED -> LIVE), regardless of which human
     * triggered the surrounding request.
     */
    public void logAsSystem(String action, String entity, String entityId, String details) {
        save(action, entity, entityId, details, "system");
    }

    private void save(String action, String entity, String entityId, String details, String username) {
        try {
            AuditEvent event = new AuditEvent();
            event.setId(UUID.randomUUID().toString());
            event.setTenantId(TenantContext.getTenantId());
            event.setProjectId(TenantContext.getProjectId());
            event.setUsername(username);
            event.setAction(action);
            event.setEntity(entity);
            event.setEntityId(entityId);
            event.setDetails(details);
            event.setCreatedAt(Instant.now());

            auditEventRepository.save(event);
        } catch (Exception e) {
            log.warn("Failed to save audit event: {}", e.getMessage());
        }
    }

    public Page<AuditEvent> getAuditLog(int page, int size) {
        return auditEventRepository.findByTenantIdOrderByCreatedAtDesc(
                TenantContext.getTenantId(),
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    public Page<AuditEvent> getAuditLogByProject(int page, int size) {
        return auditEventRepository.findByTenantIdAndProjectIdOrderByCreatedAtDesc(
                TenantContext.getTenantId(),
                TenantContext.getProjectId(),
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }
}
