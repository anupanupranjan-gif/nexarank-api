// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.repository;

import com.nexarank.api.model.ContentRule;
import com.nexarank.api.model.ContentZone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContentRuleRepository extends JpaRepository<ContentRule, String> {
    List<ContentRule> findByTenantIdAndProjectIdAndDeletedAtIsNull(String tenantId, String projectId);
    List<ContentRule> findByTenantIdAndProjectIdAndZoneInAndStatusAndDeletedAtIsNull(
            String tenantId, String projectId, List<ContentZone> zones, ContentRule.ContentRuleStatus status);
}
