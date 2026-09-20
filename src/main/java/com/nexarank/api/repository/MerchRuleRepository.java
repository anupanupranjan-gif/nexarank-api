// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.repository;

import com.nexarank.api.model.MerchRule;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

public interface MerchRuleRepository extends JpaRepository<MerchRule, String> {
    List<MerchRule> findByTenantIdAndProjectId(String tenantId, String projectId);

    /**
     * NR-184: bounded sibling of the plain findByTenantIdAndProjectId above —
     * used by getAllRules() so the "list every rule" admin screen has a
     * query-level cap instead of returning an unbounded result set as a
     * tenant's rule count grows with merchandising maturity.
     */
    List<MerchRule> findByTenantIdAndProjectId(String tenantId, String projectId, Pageable pageable);
    List<MerchRule> findByTenantIdAndProjectIdAndEnabled(String tenantId, String projectId, boolean enabled);
    List<MerchRule> findByTenantIdAndProjectIdAndStatus(String tenantId, String projectId, MerchRule.RuleStatus status);
    List<MerchRule> findByTenantIdAndProjectIdAndStatusAndEnabled(String tenantId, String projectId, MerchRule.RuleStatus status, boolean enabled);
    List<MerchRule> findByQueryAndEnabled(String query, boolean enabled);
    List<MerchRule> findByTenantIdAndProjectIdAndQueryAndEnabled(String tenantId, String projectId, String query, boolean enabled);

    @Modifying
    @Query("UPDATE MerchRule r SET r.firedCount = r.firedCount + 1, r.lastFiredAt = :firedAt WHERE r.id IN :ids")
    void incrementFiredCount(@Param("ids") List<String> ids, @Param("firedAt") Instant firedAt);
}
