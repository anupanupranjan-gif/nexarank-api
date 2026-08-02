// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.model.Judgment;
import com.nexarank.api.model.JudgmentSet;
import com.nexarank.api.repository.ClickEventRepository;
import com.nexarank.api.repository.JudgmentRepository;
import com.nexarank.api.repository.JudgmentSetRepository;
import com.nexarank.api.security.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/judgments")
public class JudgmentController {

    private final JudgmentSetRepository setRepository;
    private final JudgmentRepository judgmentRepository;
    private final ClickEventRepository clickEventRepository;

    public JudgmentController(JudgmentSetRepository setRepository,
                               JudgmentRepository judgmentRepository,
                               ClickEventRepository clickEventRepository) {
        this.setRepository = setRepository;
        this.judgmentRepository = judgmentRepository;
        this.clickEventRepository = clickEventRepository;
    }

    // ── Judgment Sets ──

    @GetMapping("/sets")
    public ResponseEntity<?> listSets() {
        return ResponseEntity.ok(setRepository.findByTenantIdAndProjectIdOrderByCreatedAtDesc(
                TenantContext.getTenantId(), TenantContext.getProjectId()));
    }

    @PostMapping("/sets")
    public ResponseEntity<?> createSet(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null) return ResponseEntity.badRequest().body(Map.of("error", "name required"));

        JudgmentSet set = new JudgmentSet();
        set.setId(UUID.randomUUID().toString());
        set.setTenantId(TenantContext.getTenantId());
        set.setProjectId(TenantContext.getProjectId());
        set.setName(name);
        set.setDescription(body.get("description"));
        set.setCreatedBy(SecurityContextHolder.getContext().getAuthentication().getName());
        set.setCreatedAt(Instant.now());
        set.setUpdatedAt(Instant.now());

        return ResponseEntity.status(HttpStatus.CREATED).body(setRepository.save(set));
    }

    @DeleteMapping("/sets/{setId}")
    public ResponseEntity<?> deleteSet(@PathVariable String setId) {
        if (!ownsSet(setId)) return ResponseEntity.notFound().build();
        setRepository.deleteById(setId);
        return ResponseEntity.noContent().build();
    }

    // ── Judgments within a set ──

    @GetMapping("/sets/{setId}/judgments")
    public ResponseEntity<?> getJudgments(@PathVariable String setId) {
        if (!ownsSet(setId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(judgmentRepository.findBySetId(setId));
    }

    @PutMapping("/sets/{setId}/judgments")
    public ResponseEntity<?> saveJudgment(@PathVariable String setId,
                                           @RequestBody Map<String, Object> body) {
        if (!ownsSet(setId)) return ResponseEntity.notFound().build();
        String query = (String) body.get("query");
        String productId = (String) body.get("productId");
        String productTitle = (String) body.get("productTitle");
        int grade = body.get("grade") != null ? ((Number) body.get("grade")).intValue() : 0;
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        Optional<Judgment> existing = judgmentRepository.findBySetIdAndQueryAndProductId(setId, query, productId);
        Judgment judgment = existing.orElse(new Judgment());

        if (judgment.getId() == null) judgment.setId(UUID.randomUUID().toString());
        judgment.setSetId(setId);
        judgment.setQuery(query);
        judgment.setProductId(productId);
        judgment.setProductTitle(productTitle);
        judgment.setGrade(grade);
        judgment.setJudgedBy(username);
        judgment.setJudgedAt(Instant.now());

        return ResponseEntity.ok(judgmentRepository.save(judgment));
    }

    // ── Top queries from click data (for curation) ──

    @GetMapping("/suggested-queries")
    public ResponseEntity<?> getSuggestedQueries(@RequestParam(defaultValue = "50") int limit) {
        String tenantId = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);

        List<Map<String, Object>> queries = clickEventRepository
                .findQueryStats(tenantId, projectId, since)
                .stream()
                .limit(limit)
                .map(row -> {
                    Map<String, Object> q = new LinkedHashMap<>();
                    q.put("query", row[0]);
                    q.put("clicks", ((Number) row[1]).longValue());
                    q.put("impressions", ((Number) row[2]).longValue());
                    return q;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(queries);
    }

    // ── Summary stats for a set ──

    @GetMapping("/sets/{setId}/stats")
    public ResponseEntity<?> getSetStats(@PathVariable String setId) {
        if (!ownsSet(setId)) return ResponseEntity.notFound().build();
        List<Judgment> judgments = judgmentRepository.findBySetId(setId);
        long totalJudgments = judgments.size();
        long queriesJudged = judgments.stream().map(Judgment::getQuery).distinct().count();
        double avgGrade = judgments.stream().mapToInt(Judgment::getGrade).average().orElse(0.0);

        return ResponseEntity.ok(Map.of(
                "totalJudgments", totalJudgments,
                "queriesJudged", queriesJudged,
                "avgGrade", Math.round(avgGrade * 100.0) / 100.0
        ));
    }

    /**
     * NR-121: per-set operations previously took a raw setId with no
     * ownership check at all — any authenticated caller who knew/guessed a
     * UUID could read/edit/delete another tenant's judgment set. 404 (not
     * 403) so a caller can't distinguish "doesn't exist" from "exists but
     * isn't yours."
     */
    private boolean ownsSet(String setId) {
        return setRepository.findByIdAndTenantIdAndProjectId(
                setId, TenantContext.getTenantId(), TenantContext.getProjectId()).isPresent();
    }
}
