// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.repository;

import com.nexarank.api.model.FacetConfig;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface FacetConfigRepository extends ElasticsearchRepository<FacetConfig, String> {
    List<FacetConfig> findByEnabledTrueOrderBySortOrderAsc();
    List<FacetConfig> findAllByOrderBySortOrderAsc();
    boolean existsByFieldName(String fieldName);
}
