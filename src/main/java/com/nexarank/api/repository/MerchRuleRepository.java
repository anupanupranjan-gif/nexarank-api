// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.repository;

import com.nexarank.api.model.MerchRule;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import java.util.List;

public interface MerchRuleRepository extends ElasticsearchRepository<MerchRule, String> {
    List<MerchRule> findByQueryAndEnabled(String query, boolean enabled);
    List<MerchRule> findByType(MerchRule.RuleType type);
    List<MerchRule> findByEnabled(boolean enabled);
}
