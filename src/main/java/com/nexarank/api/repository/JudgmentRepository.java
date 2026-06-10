// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.repository;

import com.nexarank.api.model.Judgment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface JudgmentRepository extends JpaRepository<Judgment, String> {
    List<Judgment> findBySetId(String setId);
    List<Judgment> findBySetIdAndQuery(String setId, String query);
    Optional<Judgment> findBySetIdAndQueryAndProductId(String setId, String query, String productId);
    void deleteBySetIdAndQueryAndProductId(String setId, String query, String productId);
    long countBySetId(String setId);
    List<String> findDistinctQueryBySetId(String setId);
}
