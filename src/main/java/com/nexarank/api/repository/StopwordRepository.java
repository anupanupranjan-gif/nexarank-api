// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.repository;

import com.nexarank.api.model.Stopword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StopwordRepository extends JpaRepository<Stopword, Long> {

    List<Stopword> findByTenantIdAndProjectId(String tenantId, String projectId);

    Optional<Stopword> findByTenantIdAndProjectIdAndWord(
        String tenantId, String projectId, String word);

    void deleteByTenantIdAndProjectIdAndWord(
        String tenantId, String projectId, String word);

    @Query("SELECT s.word FROM Stopword s WHERE s.tenantId = :tenantId AND s.projectId = :projectId")
    List<String> findWordsByTenantIdAndProjectId(
        @Param("tenantId") String tenantId,
        @Param("projectId") String projectId);
}
