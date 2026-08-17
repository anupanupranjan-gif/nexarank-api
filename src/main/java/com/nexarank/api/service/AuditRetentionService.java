// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.nexarank.api.model.Tenant;
import com.nexarank.api.repository.ApiAccessEventRepository;
import com.nexarank.api.repository.AuditEventRepository;
import com.nexarank.api.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NR-70: enforces per-tenant audit retention (default 90 days, enterprise 365).
 *
 * This is the ONLY code path in the application that deletes audit rows. The
 * audit log is otherwise append-only — there is no update or delete API for
 * either tier, by design, and this job deletes strictly by age rather than by
 * anything a caller can select.
 *
 * Scheduled tasks have no request scope, so tenant/project must be passed
 * explicitly rather than read from TenantContext.
 */
@Service
public class AuditRetentionService {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionService.class);

    private final AuditEventRepository auditEventRepository;
    private final ApiAccessEventRepository apiAccessEventRepository;
    private final TenantRepository tenantRepository;

    public AuditRetentionService(AuditEventRepository auditEventRepository,
                                 ApiAccessEventRepository apiAccessEventRepository,
                                 TenantRepository tenantRepository) {
        this.auditEventRepository = auditEventRepository;
        this.apiAccessEventRepository = apiAccessEventRepository;
        this.tenantRepository = tenantRepository;
    }

    /** Daily at 03:00. Overridable via nexarank.audit.retention.cron. */
    @Scheduled(cron = "${nexarank.audit.retention.cron:0 0 3 * * *}")
    public void purgeExpiredAuditRecords() {
        Map<String, Object> result = runPurge();
        log.info("Audit retention purge complete: {}", result);
    }

    /**
     * Exposed so an admin can trigger the purge on demand and see exactly what
     * it removed, rather than having to infer it from the next scheduled run.
     */
    @Transactional
    public Map<String, Object> runPurge() {
        Map<String, Object> summary = new LinkedHashMap<>();
        int totalAudit = 0;
        int totalAccess = 0;

        List<Tenant> tenants = tenantRepository.findAll();
        for (Tenant tenant : tenants) {
            int days = tenant.getAuditRetentionDays() > 0 ? tenant.getAuditRetentionDays() : 90;
            Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);

            int auditDeleted = auditEventRepository.purgeOlderThan(tenant.getId(), cutoff);
            int accessDeleted = apiAccessEventRepository.purgeOlderThan(tenant.getId(), cutoff);
            totalAudit += auditDeleted;
            totalAccess += accessDeleted;

            if (auditDeleted > 0 || accessDeleted > 0) {
                log.info("Audit retention ({}): removed {} audit events and {} API access events older than {} days",
                        tenant.getId(), auditDeleted, accessDeleted, days);
            }
        }

        summary.put("tenantsProcessed", tenants.size());
        summary.put("auditEventsDeleted", totalAudit);
        summary.put("apiAccessEventsDeleted", totalAccess);
        return summary;
    }
}
