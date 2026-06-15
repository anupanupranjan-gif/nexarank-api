// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.nexarank.api.model.BusinessSignal;
import com.nexarank.api.repository.BusinessSignalRepository;
import com.nexarank.api.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BusinessSignalService {

    private static final Logger log = LoggerFactory.getLogger(BusinessSignalService.class);

    private final BusinessSignalRepository repository;

    public BusinessSignalService(BusinessSignalRepository repository) {
        this.repository = repository;
    }

    /**
     * Get all signals for current tenant/project.
     */
    public List<BusinessSignal> getAll() {
        return repository.findByTenantIdAndProjectId(
            TenantContext.getTenantId(), TenantContext.getProjectId());
    }

    /**
     * Get currently active signals (within validFrom/validTo window).
     */
    public List<BusinessSignal> getActiveSignals() {
        return repository.findActiveSignals(
            TenantContext.getTenantId(), TenantContext.getProjectId(), Instant.now());
    }

    /**
     * Get active signals grouped by productId for fast lookup.
     */
    public Map<String, List<BusinessSignal>> getActiveSignalsByProduct() {
        return getActiveSignals().stream()
            .collect(Collectors.groupingBy(BusinessSignal::getProductId));
    }

    /**
     * Ingest a batch of signals from ERP/PIM/OMS webhook.
     * Upserts by (tenant, project, productId, signalType).
     */
    public List<BusinessSignal> ingest(List<BusinessSignal> signals, String source) {
        String tenantId  = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();
        List<BusinessSignal> saved = new ArrayList<>();

        for (BusinessSignal signal : signals) {
            signal.setTenantId(tenantId);
            signal.setProjectId(projectId);
            signal.setSource(source != null ? source : "API");
            signal.setUpdatedAt(Instant.now());

            // Upsert — find existing by productId + signalType
            repository.findByTenantIdAndProjectId(tenantId, projectId).stream()
                .filter(s -> s.getProductId().equals(signal.getProductId())
                    && s.getSignalType() == signal.getSignalType())
                .findFirst()
                .ifPresent(existing -> signal.setId(existing.getId()));

            if (signal.getId() == null) signal.setId(UUID.randomUUID().toString());
            if (signal.getCreatedAt() == null) signal.setCreatedAt(Instant.now());

            saved.add(repository.save(signal));
            log.info("SIGNAL_INGEST productId={} type={} source={}",
                signal.getProductId(), signal.getSignalType(), source);
        }
        return saved;
    }

    /**
     * Seed demo signals for testing.
     * Uses real product IDs from click_events if available.
     */
    public List<BusinessSignal> seedDemoSignals(List<String> productIds) {
        String tenantId  = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();
        Instant now      = Instant.now();

        List<BusinessSignal> demo = new ArrayList<>();

        // Use provided product IDs or fallback to generic ones
        String promoted   = productIds.size() > 0 ? productIds.get(0) : "P001";
        String outOfStock = productIds.size() > 1 ? productIds.get(1) : "P002";
        String lowMargin  = productIds.size() > 2 ? productIds.get(2) : "P003";
        String seasonal   = productIds.size() > 3 ? productIds.get(3) : "P004";

        // PROMOTED — brand agreement
        BusinessSignal p = new BusinessSignal();
        p.setProductId(promoted);
        p.setSignalType(BusinessSignal.SignalType.PROMOTED);
        p.setValue("Brand agreement — Q2 2026");
        p.setSource("demo");
        demo.add(p);

        // OUT_OF_STOCK
        BusinessSignal o = new BusinessSignal();
        o.setProductId(outOfStock);
        o.setSignalType(BusinessSignal.SignalType.OUT_OF_STOCK);
        o.setValue("0 units in warehouse");
        o.setSource("demo");
        demo.add(o);

        // MARGIN_LOW
        BusinessSignal m = new BusinessSignal();
        m.setProductId(lowMargin);
        m.setSignalType(BusinessSignal.SignalType.MARGIN_LOW);
        m.setValue("margin=3%");
        m.setSource("demo");
        demo.add(m);

        // SEASONAL — winter window
        BusinessSignal s = new BusinessSignal();
        s.setProductId(seasonal);
        s.setSignalType(BusinessSignal.SignalType.SEASONAL);
        s.setValue("Winter season");
        s.setValidFrom(now.minus(30, ChronoUnit.DAYS));
        s.setValidTo(now.plus(60, ChronoUnit.DAYS));
        s.setSource("demo");
        demo.add(s);

        return ingest(demo, "demo");
    }

    public void delete(String id) {
        repository.deleteById(id);
    }
}
