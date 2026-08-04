// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.model.Judgment;
import com.nexarank.api.model.JudgmentSet;
import com.nexarank.api.repository.ClickEventRepository;
import com.nexarank.api.repository.JudgmentRepository;
import com.nexarank.api.repository.JudgmentSetRepository;
import com.nexarank.api.security.TenantContext;
import com.nexarank.api.service.LlmJudgmentService;
import com.nexarank.api.service.SearchQualityService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/judgments")
public class JudgmentController {

    private final JudgmentSetRepository setRepository;
    private final JudgmentRepository judgmentRepository;
    private final ClickEventRepository clickEventRepository;
    private final LlmJudgmentService llmJudgmentService;
    private final SearchQualityService searchQualityService;

    public JudgmentController(JudgmentSetRepository setRepository,
                               JudgmentRepository judgmentRepository,
                               ClickEventRepository clickEventRepository,
                               LlmJudgmentService llmJudgmentService,
                               SearchQualityService searchQualityService) {
        this.setRepository = setRepository;
        this.judgmentRepository = judgmentRepository;
        this.clickEventRepository = clickEventRepository;
        this.llmJudgmentService = llmJudgmentService;
        this.searchQualityService = searchQualityService;
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

        boolean wasPendingLlmReview = existing.isPresent() && "PENDING_REVIEW".equals(judgment.getStatus());

        if (judgment.getId() == null) judgment.setId(UUID.randomUUID().toString());
        judgment.setSetId(setId);
        judgment.setQuery(query);
        judgment.setProductId(productId);
        judgment.setProductTitle(productTitle);
        judgment.setGrade(grade);
        judgment.setJudgedBy(username);
        judgment.setJudgedAt(Instant.now());

        // NR-58: a human directly editing a judgment (new or previously-LLM-
        // authored) counts as review — matches the dedicated /review endpoint's
        // semantics so the two entry points can't leave inconsistent state.
        judgment.setStatus("APPROVED");
        if (wasPendingLlmReview) {
            judgment.setReviewedBy(username);
            judgment.setReviewedAt(Instant.now());
        }

        return ResponseEntity.ok(judgmentRepository.save(judgment));
    }

    /**
     * NR-58 — auto-score the top-N live results for a query using the
     * configured LLM. Saves as PENDING_REVIEW; never auto-applied to a
     * final grade without human review.
     */
    @PostMapping("/sets/{setId}/auto-score")
    public ResponseEntity<?> autoScore(@PathVariable String setId, @RequestBody Map<String, Object> body) {
        if (!ownsSet(setId)) return ResponseEntity.notFound().build();
        String query = (String) body.get("query");
        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "query required"));
        }
        int topN = body.get("topN") != null ? ((Number) body.get("topN")).intValue() : 10;
        try {
            return ResponseEntity.ok(llmJudgmentService.autoScore(setId, query, topN));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * NR-58 — human review of an LLM-authored judgment: accept as-is (omit
     * grade) or override (include a different grade). Either way marks the
     * judgment APPROVED and stamps reviewedBy/reviewedAt; llmGrade is never
     * touched, so agreement-rate tracking always has the original AI answer.
     */
    @PatchMapping("/sets/{setId}/judgments/{judgmentId}/review")
    public ResponseEntity<?> reviewJudgment(@PathVariable String setId, @PathVariable String judgmentId,
                                             @RequestBody(required = false) Map<String, Object> body) {
        if (!ownsSet(setId)) return ResponseEntity.notFound().build();
        Optional<Judgment> found = judgmentRepository.findById(judgmentId);
        if (found.isEmpty() || !setId.equals(found.get().getSetId())) return ResponseEntity.notFound().build();

        Judgment judgment = found.get();
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        if (body != null && body.get("grade") != null) {
            judgment.setGrade(((Number) body.get("grade")).intValue());
        }
        judgment.setStatus("APPROVED");
        judgment.setReviewedBy(username);
        judgment.setReviewedAt(Instant.now());

        return ResponseEntity.ok(judgmentRepository.save(judgment));
    }

    /**
     * NR-58 — agreement rate between LLM-suggested and human-reviewed final
     * grades, overall and bucketed by review day (the "over time" tracking
     * the ticket asks for). Only counts judgments a human has actually
     * reviewed (APPROVED) — a PENDING_REVIEW LLM judgment hasn't been
     * compared against a human opinion yet.
     */
    @GetMapping("/sets/{setId}/agreement-rate")
    public ResponseEntity<?> getAgreementRate(@PathVariable String setId) {
        if (!ownsSet(setId)) return ResponseEntity.notFound().build();
        List<Judgment> reviewed = judgmentRepository.findBySetId(setId).stream()
                .filter(j -> "LLM".equals(j.getSource()) && "APPROVED".equals(j.getStatus()) && j.getLlmGrade() != null)
                .toList();

        long total = reviewed.size();
        long agreed = reviewed.stream().filter(j -> j.getGrade() == j.getLlmGrade()).count();

        Map<String, long[]> byDay = new TreeMap<>();
        for (Judgment j : reviewed) {
            String day = (j.getReviewedAt() != null ? j.getReviewedAt() : j.getJudgedAt())
                    .atZone(ZoneOffset.UTC).toLocalDate().toString();
            long[] counts = byDay.computeIfAbsent(day, k -> new long[2]); // {agreed, total}
            counts[1]++;
            if (j.getGrade() == j.getLlmGrade()) counts[0]++;
        }
        List<Map<String, Object>> trend = byDay.entrySet().stream().map(e -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("day", e.getKey());
            row.put("agreed", e.getValue()[0]);
            row.put("total", e.getValue()[1]);
            row.put("rate", e.getValue()[1] == 0 ? 0.0 : Math.round((double) e.getValue()[0] / e.getValue()[1] * 1000.0) / 1000.0);
            return row;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalReviewed", total);
        result.put("agreed", agreed);
        result.put("overallRate", total == 0 ? null : Math.round((double) agreed / total * 1000.0) / 1000.0);
        result.put("trend", trend);
        return ResponseEntity.ok(result);
    }

    /**
     * NR-58 — compute NDCG@5/@10 and MRR@10 from this judgment set's own
     * approved judgments against live search results, distinct from
     * SearchQualityService's separate hardcoded 30-query benchmark. Computed
     * on-demand (not persisted) since it's scoped to one set, not a
     * tenant/project-wide "latest" metric the Analytics dashboard's existing
     * KPI already represents.
     */
    @GetMapping("/sets/{setId}/ndcg")
    public ResponseEntity<?> getNdcgForSet(@PathVariable String setId) {
        if (!ownsSet(setId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(searchQualityService.evaluateFromJudgmentSet(setId,
                judgmentRepository.findBySetId(setId)));
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
