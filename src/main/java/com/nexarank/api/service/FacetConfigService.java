// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;
import com.nexarank.api.security.TenantContext;

import com.nexarank.api.model.FacetConfig;
import java.util.UUID;
import com.nexarank.api.repository.FacetConfigRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class FacetConfigService {

    private final FacetConfigRepository repository;

    public FacetConfigService(FacetConfigRepository repository) {
        this.repository = repository;
    }

    public List<FacetConfig> getAllFacets() {
        return repository.findByTenantIdAndProjectIdOrderBySortOrderAsc(TenantContext.getTenantId(), TenantContext.getProjectId());
    }

    public List<FacetConfig> getEnabledFacets() {
        return repository.findByTenantIdAndProjectIdAndEnabledOrderBySortOrderAsc(TenantContext.getTenantId(), TenantContext.getProjectId(), true);
    }

    public Optional<FacetConfig> getById(String id) {
        return findScopedById(id);
    }

    /**
     * NR-162 companion fix: same class of by-ID scoping gap found in
     * MerchRuleService — findById(id) had no tenant/project filter, so any
     * ADMIN could read/update/toggle/delete another project's (or another
     * tenant's) facet config just by id. deleteFacet() was worse still: it
     * called repository.deleteById(id) with no lookup at all.
     */
    private Optional<FacetConfig> findScopedById(String id) {
        return repository.findById(id)
                .filter(f -> java.util.Objects.equals(f.getTenantId(), TenantContext.getTenantId())
                        && java.util.Objects.equals(f.getProjectId(), TenantContext.getProjectId()));
    }

    public record ImportResult(FacetConfig facet, boolean idCollisionResolved) {}

    /** NR-167 (config import): same upsert-by-id-within-current-tenant/project pattern as MerchRuleService.importRule. */
    public ImportResult importFacet(com.nexarank.api.configexport.dto.FacetExport dto) {
        String tenantId = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();

        Optional<FacetConfig> existingAnywhere = repository.findById(dto.id());
        boolean idCollision = existingAnywhere.isPresent() &&
                !(tenantId.equals(existingAnywhere.get().getTenantId())
                        && projectId.equals(existingAnywhere.get().getProjectId()));

        FacetConfig facet;
        if (existingAnywhere.isPresent() && !idCollision) {
            facet = existingAnywhere.get();
        } else {
            facet = new FacetConfig();
            facet.setId(idCollision ? UUID.randomUUID().toString() : dto.id());
            facet.setTenantId(tenantId);
            facet.setProjectId(projectId);
            facet.setCreatedAt(Instant.now());
        }

        facet.setFieldName(dto.fieldName());
        facet.setDisplayLabel(dto.displayLabel());
        facet.setFacetType(FacetConfig.FacetType.valueOf(dto.facetType()));
        facet.setEnabled(dto.enabled());
        facet.setShowCount(dto.showCount());
        facet.setSortOrder(dto.sortOrder());
        facet.setMaxValues(dto.maxValues());
        facet.setRangeMin(dto.rangeMin());
        facet.setRangeMax(dto.rangeMax());
        facet.setRangeInterval(dto.rangeInterval());
        facet.setUpdatedAt(Instant.now());

        return new ImportResult(repository.save(facet), idCollision);
    }

    public FacetConfig createFacet(FacetConfig facet) {
        if (facet.getId() == null) facet.setId(UUID.randomUUID().toString());
        if (facet.getTenantId() == null) facet.setTenantId(TenantContext.getTenantId());
        if (facet.getProjectId() == null) facet.setProjectId(TenantContext.getProjectId());
        if (repository.existsByFieldName(facet.getFieldName())) {
            throw new IllegalArgumentException("Facet already exists for field: " + facet.getFieldName());
        }
        facet.setCreatedAt(Instant.now());
        facet.setUpdatedAt(Instant.now());
        return repository.save(facet);
    }

    public Optional<FacetConfig> updateFacet(String id, FacetConfig updated) {
        return findScopedById(id).map(existing -> {
            updated.setId(existing.getId());
            updated.setTenantId(existing.getTenantId());
            updated.setProjectId(existing.getProjectId());
            updated.setCreatedAt(existing.getCreatedAt());
            updated.setUpdatedAt(Instant.now());
            return repository.save(updated);
        });
    }

    public Optional<FacetConfig> toggleFacet(String id) {
        return findScopedById(id).map(facet -> {
            facet.setEnabled(!facet.isEnabled());
            facet.setUpdatedAt(Instant.now());
            return repository.save(facet);
        });
    }

    public void deleteFacet(String id) {
        findScopedById(id).ifPresent(f -> repository.deleteById(id));
    }

    public void seedDefaultFacets() {
        if (repository.count() > 0) return;

        String[][] defaults = {
            // .keyword: ES's default dynamic mapping makes string fields analyzed
            // "text" with a "fielddata: false" (default) sibling "keyword" multi-field.
            // A terms aggregation directly against the bare text field either errors
            // ("Fielddata is disabled on text fields by default") or, if fielddata is
            // force-enabled, buckets by individual tokens instead of whole values -
            // found live 2026-08-20 (nexarank/rebuild session) when this exact bug
            // silently broke Facet Manager + TriggerConditionBuilder for a from-scratch
            // tenant using search-catalog-indexer's stock ES mapping.
            {"category.keyword",  "Category",      "TERMS",   "1", "10",  null,    null,   null,  "true"},
            {"brand.keyword",     "Brand",         "TERMS",   "2", "10",  null,    null,   null,  "true"},
            {"price",     "Price Range",   "RANGE",   "3", null,  "0",     "500",  "50",  "false"},
            {"rating",    "Avg. Rating",   "RANGE",   "4", null,  "0",     "5",    "1",   "false"},
        };

        for (String[] d : defaults) {
            FacetConfig f = new FacetConfig();
            f.setId(UUID.randomUUID().toString());
            f.setTenantId(TenantContext.getTenantId());
            f.setProjectId(TenantContext.getProjectId());
            f.setFieldName(d[0]);
            f.setDisplayLabel(d[1]);
            f.setFacetType(FacetConfig.FacetType.valueOf(d[2]));
            f.setSortOrder(Integer.parseInt(d[3]));
            f.setEnabled(true);
            if (d[4] != null) f.setMaxValues(Integer.parseInt(d[4]));
            if (d[5] != null) f.setRangeMin(Double.parseDouble(d[5]));
            if (d[6] != null) f.setRangeMax(Double.parseDouble(d[6]));
            if (d[7] != null) f.setRangeInterval(Double.parseDouble(d[7]));
            f.setShowCount(Boolean.parseBoolean(d[8]));
            f.setCreatedAt(Instant.now());
            f.setUpdatedAt(Instant.now());
            repository.save(f);
        }
    }
}
