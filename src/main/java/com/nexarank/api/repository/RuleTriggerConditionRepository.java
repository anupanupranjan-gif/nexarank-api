// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.repository;

import com.nexarank.api.model.RuleTriggerCondition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RuleTriggerConditionRepository extends JpaRepository<RuleTriggerCondition, String> {

    List<RuleTriggerCondition> findByRuleIdOrderByPosition(String ruleId);

    void deleteByRuleId(String ruleId);
}
