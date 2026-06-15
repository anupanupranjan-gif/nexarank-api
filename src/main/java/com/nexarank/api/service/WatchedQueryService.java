// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.nexarank.api.model.WatchedQuery;
import com.nexarank.api.repository.WatchedQueryRepository;
import com.nexarank.api.security.TenantContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class WatchedQueryService {

    private final WatchedQueryRepository repository;

    public WatchedQueryService(WatchedQueryRepository repository) {
        this.repository = repository;
    }

    public List<WatchedQuery> getAll() {
        return repository.findByTenantIdAndProjectId(
            TenantContext.getTenantId(), TenantContext.getProjectId());
    }

    public List<WatchedQuery> getEnabled() {
        return repository.findByTenantIdAndProjectIdAndEnabled(
            TenantContext.getTenantId(), TenantContext.getProjectId(), true);
    }

    public WatchedQuery add(WatchedQuery wq) {
        String tenantId  = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();

        // Idempotent — update if already exists
        Optional<WatchedQuery> existing = repository
            .findByTenantIdAndProjectIdAndQuery(tenantId, projectId, wq.getQuery());
        existing.ifPresent(e -> wq.setId(e.getId()));

        if (wq.getId() == null) wq.setId(UUID.randomUUID().toString());
        wq.setTenantId(tenantId);
        wq.setProjectId(projectId);
        wq.setCreatedBy(currentUser());
        if (wq.getCreatedAt() == null) wq.setCreatedAt(Instant.now());
        wq.setUpdatedAt(Instant.now());
        return repository.save(wq);
    }

    public void delete(String id) {
        repository.deleteByTenantIdAndProjectIdAndId(
            TenantContext.getTenantId(), TenantContext.getProjectId(), id);
    }

    public Optional<WatchedQuery> toggleEnabled(String id) {
        return repository.findById(id).map(wq -> {
            wq.setEnabled(!wq.isEnabled());
            wq.setUpdatedAt(Instant.now());
            return repository.save(wq);
        });
    }

    private String currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }

    public List<WatchedQuery> getAllForTenant(String tenantId, String projectId) {
        return repository.findByTenantIdAndProjectId(tenantId, projectId);
    }
}
