// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.repository;

import com.nexarank.api.model.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, String> {
    Page<AuditEvent> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);
    Page<AuditEvent> findByTenantIdAndProjectIdOrderByCreatedAtDesc(String tenantId, String projectId, Pageable pageable);
}
