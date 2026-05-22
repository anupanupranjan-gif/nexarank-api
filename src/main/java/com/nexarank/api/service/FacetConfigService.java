// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.nexarank.api.model.FacetConfig;
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
        return repository.findAllByOrderBySortOrderAsc();
    }

    public List<FacetConfig> getEnabledFacets() {
        return repository.findByEnabledTrueOrderBySortOrderAsc();
    }

    public Optional<FacetConfig> getById(String id) {
        return repository.findById(id);
    }

    public FacetConfig createFacet(FacetConfig facet) {
        if (repository.existsByFieldName(facet.getFieldName())) {
            throw new IllegalArgumentException("Facet already exists for field: " + facet.getFieldName());
        }
        facet.setCreatedAt(Instant.now());
        facet.setUpdatedAt(Instant.now());
        return repository.save(facet);
    }

    public Optional<FacetConfig> updateFacet(String id, FacetConfig updated) {
        return repository.findById(id).map(existing -> {
            updated.setId(existing.getId());
            updated.setCreatedAt(existing.getCreatedAt());
            updated.setUpdatedAt(Instant.now());
            return repository.save(updated);
        });
    }

    public Optional<FacetConfig> toggleFacet(String id) {
        return repository.findById(id).map(facet -> {
            facet.setEnabled(!facet.isEnabled());
            facet.setUpdatedAt(Instant.now());
            return repository.save(facet);
        });
    }

    public void deleteFacet(String id) {
        repository.deleteById(id);
    }

    public void seedDefaultFacets() {
        if (repository.count() > 0) return;

        String[][] defaults = {
            {"category",  "Category",      "TERMS",   "1", "10",  null,    null,   null,  "true"},
            {"brand",     "Brand",         "TERMS",   "2", "10",  null,    null,   null,  "true"},
            {"price",     "Price Range",   "RANGE",   "3", null,  "0",     "500",  "50",  "false"},
            {"rating",    "Avg. Rating",   "RANGE",   "4", null,  "0",     "5",    "1",   "false"},
        };

        for (String[] d : defaults) {
            FacetConfig f = new FacetConfig();
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
