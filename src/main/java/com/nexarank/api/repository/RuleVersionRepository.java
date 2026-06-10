// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.repository;

import com.nexarank.api.model.RuleVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RuleVersionRepository extends JpaRepository<RuleVersion, String> {

    List<RuleVersion> findByRuleIdOrderByVersionNumberDesc(String ruleId);

    Optional<RuleVersion> findByRuleIdAndVersionNumber(String ruleId, int versionNumber);

    Optional<RuleVersion> findTopByRuleIdOrderByVersionNumberDesc(String ruleId);
}
