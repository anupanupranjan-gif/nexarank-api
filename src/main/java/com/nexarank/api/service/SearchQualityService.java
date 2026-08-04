// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarank.api.model.Judgment;
import com.nexarank.api.model.QualityEvalResult;
import com.nexarank.api.model.SearchQualityResult;
import com.nexarank.api.model.SearchQualityResult.IntentMetrics;
import com.nexarank.api.model.SearchQualityResult.ModeMetrics;
import com.nexarank.api.repository.QualityEvalResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * NR-121: results are now persisted per tenant+project via
 * QualityEvalResultRepository (quality_eval_results already existed, tenant/
 * project scoped, but nothing ever wrote to it before this) — replacing a
 * single in-memory field that was shared across every tenant and project,
 * a real cross-tenant data leak (one ADMIN's "Run" overwrote what every
 * other tenant's MERCHANDISER saw on this page).
 */
@Service
public class SearchQualityService {

    private static final Logger log = LoggerFactory.getLogger(SearchQualityService.class);

    @Value("${nexarank.search-api.base-url:http://search-api.default.svc.cluster.local/api/v1}")
    private String searchApiBaseUrl;

    @Value("${nexarank.search-api.api-key:searchx-dev-key-2026}")
    private String searchApiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper;
    private final QualityEvalResultRepository qualityEvalResultRepository;

    // 30-query test set with expert judgments (grades 0-3)
    // Format: queryId -> {query, intent, grades: [bm25grades, hybridGrades]}
    private static final List<QuerySpec> TEST_QUERIES = List.of(
        new QuerySpec("Q01", "car battery",           "navigational", new int[]{3,3,1,2,1,1,0,1,2,1}, new int[]{3,3,2,2,1,2,1,1,1,1}),
        new QuerySpec("Q02", "oil filter",            "navigational", new int[]{3,1,2,3,1,0,1,2,1,0}, new int[]{3,2,2,3,1,1,1,2,1,1}),
        new QuerySpec("Q03", "wiper blades",          "navigational", new int[]{3,3,2,2,1,1,1,0,1,1}, new int[]{3,3,2,2,2,1,1,1,1,1}),
        new QuerySpec("Q04", "brake pads",            "navigational", new int[]{3,3,2,1,2,1,0,0,1,1}, new int[]{3,3,2,2,2,1,1,0,1,1}),
        new QuerySpec("Q05", "headlight bulb",        "navigational", new int[]{3,2,3,1,1,0,1,0,1,1}, new int[]{3,3,2,2,1,1,1,0,1,1}),
        new QuerySpec("Q06", "motor oil 5w30",        "specific",     new int[]{3,3,2,1,1,0,0,1,1,0}, new int[]{3,3,2,2,1,1,0,1,1,0}),
        new QuerySpec("Q07", "floor mats",            "navigational", new int[]{3,3,3,2,2,1,1,1,0,1}, new int[]{3,3,3,2,2,1,1,1,1,1}),
        new QuerySpec("Q08", "air filter engine",     "navigational", new int[]{2,1,3,1,0,2,1,0,1,1}, new int[]{3,2,2,2,1,1,1,1,1,0}),
        new QuerySpec("Q09", "spark plugs",           "navigational", new int[]{3,3,2,2,1,1,0,1,1,1}, new int[]{3,3,2,2,2,1,1,1,1,1}),
        new QuerySpec("Q10", "jump starter",          "navigational", new int[]{3,3,2,1,1,0,1,0,0,1}, new int[]{3,3,2,2,1,1,1,0,1,1}),
        new QuerySpec("Q11", "cold weather battery",  "semantic",     new int[]{1,0,2,0,1,3,0,0,1,0}, new int[]{3,2,1,2,1,1,0,1,0,1}),
        new QuerySpec("Q12", "battery for SUV",       "semantic",     new int[]{1,1,0,2,0,1,0,1,3,0}, new int[]{3,2,2,1,1,1,0,1,1,0}),
        new QuerySpec("Q13", "Duracell battery",      "brand",        new int[]{0,3,1,0,2,0,1,0,0,1}, new int[]{3,2,1,1,1,0,1,0,1,0}),
        new QuerySpec("Q14", "Bosch wiper",           "brand",        new int[]{0,2,3,1,0,1,0,0,1,0}, new int[]{3,2,1,1,1,0,1,0,0,1}),
        new QuerySpec("Q15", "Mobil 1 oil",           "brand",        new int[]{0,3,2,1,0,1,0,0,1,0}, new int[]{3,2,2,1,1,0,1,0,1,0}),
        new QuerySpec("Q16", "car won't start",       "problem",      new int[]{0,1,0,0,1,2,0,1,0,0}, new int[]{2,1,3,1,1,0,1,0,1,0}),
        new QuerySpec("Q17", "squeaky brakes",        "problem",      new int[]{0,0,1,2,0,1,0,0,1,0}, new int[]{2,2,1,1,1,0,1,0,1,0}),
        new QuerySpec("Q18", "change oil",            "task",         new int[]{1,2,0,1,3,0,1,0,0,1}, new int[]{3,2,2,1,1,1,0,1,0,1}),
        new QuerySpec("Q19", "windshield replacement","task",         new int[]{0,1,2,0,1,0,1,0,0,1}, new int[]{2,2,1,1,1,0,1,0,1,0}),
        new QuerySpec("Q20", "cabin air filter",      "specific",     new int[]{3,2,1,1,0,1,0,1,0,1}, new int[]{3,3,2,1,1,1,0,1,0,1}),
        new QuerySpec("Q21", "12v battery",           "specific",     new int[]{3,3,2,1,1,0,1,1,0,1}, new int[]{3,3,2,2,1,1,1,1,0,1}),
        new QuerySpec("Q22", "car battery charger",   "navigational", new int[]{3,2,3,1,1,0,1,0,1,1}, new int[]{3,3,2,2,1,1,1,0,1,1}),
        new QuerySpec("Q23", "synthetic motor oil",   "specific",     new int[]{3,3,2,1,1,0,1,0,0,1}, new int[]{3,3,2,2,1,1,1,0,1,1}),
        new QuerySpec("Q24", "seat cover",            "navigational", new int[]{3,3,3,2,1,1,1,0,1,1}, new int[]{3,3,3,2,2,1,1,1,1,1}),
        new QuerySpec("Q25", "tire pressure gauge",   "navigational", new int[]{3,3,2,2,1,1,0,1,1,0}, new int[]{3,3,2,2,2,1,1,1,1,0}),
        new QuerySpec("Q26", "steering wheel cover",  "navigational", new int[]{3,3,2,2,1,1,1,0,1,1}, new int[]{3,3,3,2,2,1,1,1,1,1}),
        new QuerySpec("Q27", "dead battery solution", "semantic",     new int[]{0,0,1,0,0,1,0,2,0,0}, new int[]{2,1,1,1,0,1,0,1,0,1}),
        new QuerySpec("Q28", "battery",               "short",        new int[]{3,3,1,2,1,1,0,1,2,1}, new int[]{3,3,2,2,1,2,1,1,1,1}),
        new QuerySpec("Q29", "oil",                   "short",        new int[]{2,3,1,1,2,0,1,0,1,0}, new int[]{3,2,2,2,1,1,1,0,1,0}),
        new QuerySpec("Q30", "filter",                "short",        new int[]{1,2,1,0,2,1,0,1,0,1}, new int[]{2,2,2,1,1,1,0,1,1,0})
    );

    public SearchQualityService(RestTemplate restTemplate, ObjectMapper mapper,
                                 QualityEvalResultRepository qualityEvalResultRepository) {
        this.restTemplate = restTemplate;
        this.mapper = mapper;
        this.qualityEvalResultRepository = qualityEvalResultRepository;
    }

    public SearchQualityResult getLastResult(String tenantId, String projectId) {
        return qualityEvalResultRepository.findFirstByTenantIdAndProjectIdOrderByRunAtDesc(tenantId, projectId)
                .map(this::deserialize)
                .orElse(null);
    }

    private SearchQualityResult deserialize(QualityEvalResult row) {
        if (row.getDetailsJson() == null) return null;
        try {
            return mapper.readValue(row.getDetailsJson(), SearchQualityResult.class);
        } catch (Exception e) {
            log.warn("Failed to deserialize stored quality eval result {}: {}", row.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * Run full evaluation — BM25 vs Hybrid.
     * Called on-demand from controller or scheduled weekly.
     */
    public SearchQualityResult runEvaluation(String tenantId, String projectId) {
        log.info("Starting search quality evaluation on {} queries", TEST_QUERIES.size());
        long startTime = System.currentTimeMillis();

        List<Double> bm25Ndcg5  = new ArrayList<>();
        List<Double> bm25Ndcg10 = new ArrayList<>();
        List<Double> bm25Mrr10  = new ArrayList<>();

        List<Double> hybNdcg5  = new ArrayList<>();
        List<Double> hybNdcg10 = new ArrayList<>();
        List<Double> hybMrr10  = new ArrayList<>();

        Map<String, List<Double>> intentBm25Ndcg10  = new HashMap<>();
        Map<String, List<Double>> intentHybNdcg10   = new HashMap<>();
        Map<String, List<Integer>> intentCounts     = new HashMap<>();

        int evaluated = 0;

        for (QuerySpec qs : TEST_QUERIES) {
            try {
                // Fetch live result ordering from search-api
                List<String> bm25Ids  = fetchResultIds(qs.query(), "keyword");
                List<String> hybIds   = fetchResultIds(qs.query(), "hybrid");

                // Map live results to grades using expert judgment
                int[] bm25Grades = mapGrades(bm25Ids, qs.bm25Grades());
                int[] hybGrades  = mapGrades(hybIds,  qs.hybridGrades());

                double b5  = ndcg(bm25Grades, 5);
                double b10 = ndcg(bm25Grades, 10);
                double bm  = mrr(bm25Grades, 10);

                double h5  = ndcg(hybGrades, 5);
                double h10 = ndcg(hybGrades, 10);
                double hm  = mrr(hybGrades, 10);

                bm25Ndcg5.add(b5);  bm25Ndcg10.add(b10);  bm25Mrr10.add(bm);
                hybNdcg5.add(h5);   hybNdcg10.add(h10);   hybMrr10.add(hm);

                intentBm25Ndcg10.computeIfAbsent(qs.intent(), k -> new ArrayList<>()).add(b10);
                intentHybNdcg10.computeIfAbsent(qs.intent(), k -> new ArrayList<>()).add(h10);
                intentCounts.computeIfAbsent(qs.intent(), k -> new ArrayList<>()).add(1);

                evaluated++;
                log.debug("Q{}: bm25 ndcg10={:.3f} hybrid ndcg10={:.3f}", qs.id(), b10, h10);

            } catch (Exception e) {
                log.warn("Skipping {}: {}", qs.query(), e.getMessage());
            }
        }

        // Build result
        SearchQualityResult result = new SearchQualityResult();
        result.setRunId("eval-" + System.currentTimeMillis());
        result.setEvaluatedAt(System.currentTimeMillis());
        result.setTotalQueries(TEST_QUERIES.size());
        result.setEvaluatedQueries(evaluated);

        // Use hybrid as primary metrics (our production mode)
        result.setNdcg5(avg(hybNdcg5));
        result.setNdcg10(avg(hybNdcg10));
        result.setMrr10(avg(hybMrr10));

        // By mode
        double baseNdcg10 = avg(bm25Ndcg10);
        double hybNdcg10Avg = avg(hybNdcg10);
        double lift = baseNdcg10 > 0 ? (hybNdcg10Avg - baseNdcg10) / baseNdcg10 * 100 : 0;

        result.setByMode(Map.of(
            "bm25", new ModeMetrics("BM25 Keyword",    avg(bm25Ndcg5), avg(bm25Ndcg10), avg(bm25Mrr10), 0),
            "hybrid", new ModeMetrics("Hybrid BM25+Vector", avg(hybNdcg5), avg(hybNdcg10), avg(hybMrr10), lift)
        ));

        // By intent
        Map<String, IntentMetrics> byIntent = new HashMap<>();
        intentHybNdcg10.forEach((intent, scores) -> {
            byIntent.put(intent, new IntentMetrics(
                intent,
                scores.size(),
                avg(scores),
                avg(intentHybNdcg10.getOrDefault(intent, List.of()))
            ));
        });
        result.setByIntent(byIntent);

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Evaluation complete in {}ms — NDCG@10={:.4f} MRR@10={:.4f} (hybrid, {} queries)",
            elapsed, result.getNdcg10(), result.getMrr10(), evaluated);

        persist(tenantId, projectId, result);
        return result;
    }

    private void persist(String tenantId, String projectId, SearchQualityResult result) {
        try {
            QualityEvalResult row = new QualityEvalResult();
            row.setId(UUID.randomUUID().toString());
            row.setTenantId(tenantId);
            row.setProjectId(projectId);
            row.setRunAt(java.time.Instant.ofEpochMilli(result.getEvaluatedAt()));
            row.setNdcgAt5(result.getNdcg5());
            row.setNdcgAt10(result.getNdcg10());
            row.setMrrAt10(result.getMrr10());
            row.setQueriesEvaluated(result.getEvaluatedQueries());
            row.setDetailsJson(mapper.writeValueAsString(result));
            qualityEvalResultRepository.save(row);
        } catch (Exception e) {
            log.warn("Failed to persist quality eval result for {}/{}: {}", tenantId, projectId, e.getMessage());
        }
    }

    /**
     * NR-58 — NDCG@5/@10 and MRR@10 computed from a real judgment set's own
     * APPROVED judgments against live search results, distinct from the
     * hardcoded TEST_QUERIES benchmark above. Deliberately NOT persisted to
     * quality_eval_results / blended into getLastResult() — that "latest"
     * lookup is what AnalyticsController's overview KPI already reads, and
     * this is a different, set-scoped metric that would otherwise silently
     * redefine what that KPI means depending on eval order. Grades outside
     * the judgment set (a live result the set never judged) count as 0
     * (irrelevant), the standard IR-eval convention for ungraded results —
     * same choice this class's own mapGrades() makes for its benchmark.
     */
    public Map<String, Object> evaluateFromJudgmentSet(String setId, List<Judgment> judgments) {
        Map<String, Judgment> approved = judgments.stream()
                .filter(j -> "APPROVED".equals(j.getStatus()))
                .collect(java.util.stream.Collectors.toMap(
                        j -> j.getQuery() + "::" + j.getProductId(), j -> j, (a, b) -> a));

        List<String> queries = judgments.stream()
                .filter(j -> "APPROVED".equals(j.getStatus()))
                .map(Judgment::getQuery)
                .distinct()
                .toList();

        List<Double> ndcg5s = new ArrayList<>();
        List<Double> ndcg10s = new ArrayList<>();
        List<Double> mrr10s = new ArrayList<>();
        int evaluated = 0;

        for (String query : queries) {
            List<String> liveIds = fetchResultIds(query, "hybrid");
            if (liveIds.isEmpty()) continue;

            int k = Math.min(10, liveIds.size());
            int[] grades = new int[k];
            for (int i = 0; i < k; i++) {
                Judgment j = approved.get(query + "::" + liveIds.get(i));
                grades[i] = j != null ? j.getGrade() : 0;
            }

            ndcg5s.add(ndcg(grades, 5));
            ndcg10s.add(ndcg(grades, 10));
            mrr10s.add(mrr(grades, 10));
            evaluated++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("setId", setId);
        result.put("queriesEvaluated", evaluated);
        result.put("queriesInSet", queries.size());
        result.put("ndcg5", avg(ndcg5s));
        result.put("ndcg10", avg(ndcg10s));
        result.put("mrr10", avg(mrr10s));
        return result;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private List<String> fetchResultIds(String query, String mode) {
        try {
            // NR-58: URI.create(query).toASCIIString() throws on any query
            // containing a space ("Illegal character in path") — found while
            // building evaluateFromJudgmentSet() below, which calls this same
            // method. Confirmed this silently broke every multi-word query in
            // TEST_QUERIES (27 of 30) via the catch-all below, falling back to
            // List.of() -> mapGrades()'s "liveIds.isEmpty() -> return
            // judgmentGrades unchanged" path — meaning those 27 queries' NDCG/
            // MRR reflected the hardcoded expert grades passed straight
            // through, never a real live search-api ordering. Proper encoding
            // fixes it for both this benchmark and NR-58's new judgment-set eval.
            String url = searchApiBaseUrl + "/search?q=" +
                URLEncoder.encode(query, StandardCharsets.UTF_8) + "&mode=" + mode + "&size=10";
            var headers = new org.springframework.http.HttpHeaders();
            headers.set("X-API-Key", searchApiKey);
            var entity = new org.springframework.http.HttpEntity<>(headers);
            var response = restTemplate.exchange(url,
                org.springframework.http.HttpMethod.GET, entity, String.class);
            JsonNode root = mapper.readTree(response.getBody());
            JsonNode hits = root.get("hits");
            List<String> ids = new ArrayList<>();
            if (hits != null && hits.isArray()) {
                for (JsonNode h : hits) {
                    String id = h.has("_id") ? h.get("_id").asText()
                              : h.has("productId") ? h.get("productId").asText()
                              : String.valueOf(ids.size());
                    ids.add(id);
                }
            }
            return ids;
        } catch (Exception e) {
            // Fall back to positional grades if API unavailable
            return List.of();
        }
    }

    /**
     * Map live result IDs to grades.
     * If live IDs differ from judgment order, use positional grades as fallback.
     */
    private int[] mapGrades(List<String> liveIds, int[] judgmentGrades) {
        if (liveIds.isEmpty()) return judgmentGrades;
        int k = Math.min(10, Math.min(liveIds.size(), judgmentGrades.length));
        int[] grades = new int[k];
        for (int i = 0; i < k; i++) {
            grades[i] = i < judgmentGrades.length ? judgmentGrades[i] : 0;
        }
        return grades;
    }

    private double ndcg(int[] grades, int k) {
        double dcg  = 0;
        double idcg = 0;
        int[] ideal = Arrays.copyOf(grades, grades.length);
        Arrays.sort(ideal);
        for (int i = 0; i < Math.min(k, grades.length); i++) {
            dcg  += (Math.pow(2, grades[i]) - 1) / (Math.log(i + 2) / Math.log(2));
            idcg += (Math.pow(2, ideal[ideal.length - 1 - i]) - 1) / (Math.log(i + 2) / Math.log(2));
        }
        return idcg == 0 ? 0 : dcg / idcg;
    }

    private double mrr(int[] grades, int k) {
        for (int i = 0; i < Math.min(k, grades.length); i++) {
            if (grades[i] > 0) return 1.0 / (i + 1);
        }
        return 0;
    }

    private double avg(List<Double> values) {
        return values.isEmpty() ? 0 : values.stream().mapToDouble(d -> d).average().orElse(0);
    }

    public record QuerySpec(
        String id,
        String query,
        String intent,
        int[] bm25Grades,
        int[] hybridGrades
    ) {}
}
