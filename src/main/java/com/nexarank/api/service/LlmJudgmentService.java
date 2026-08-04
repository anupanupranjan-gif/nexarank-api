// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarank.api.adapter.LlmAdapterFactory;
import com.nexarank.api.model.Judgment;
import com.nexarank.api.model.LlmConfig;
import com.nexarank.api.port.LlmPort;
import com.nexarank.api.repository.JudgmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * NR-58 — LLM auto-scoring for judgment sets. Fetches the live top-N search
 * results for a query (same search-api call pattern as SearchQualityService's
 * benchmark eval) and asks the LLM to grade each result's relevance, on a
 * 5-point PERFECT/EXCELLENT/GOOD/FAIR/BAD scale, reusing LlmPort.classify()
 * (NR-56) rather than adding a fourth near-identical LlmPort method — a
 * relevance grade is just a different label set to classify against.
 *
 * Results are saved as PENDING_REVIEW — never auto-applied to `grade`
 * silently. A human must explicitly approve or override via
 * JudgmentController's /review endpoint before a judgment counts as final.
 */
@Service
public class LlmJudgmentService {

    private static final Logger log = LoggerFactory.getLogger(LlmJudgmentService.class);

    // Ordered PERFECT..BAD so a raw response containing multiple label
    // substrings (rare, but seen with small models) resolves to the most
    // specific/confident match first.
    private static final List<String> GRADE_LABELS = List.of("PERFECT", "EXCELLENT", "GOOD", "FAIR", "BAD");

    private static final Map<String, Integer> LABEL_TO_GRADE = Map.of(
            "PERFECT", 4, "EXCELLENT", 3, "GOOD", 2, "FAIR", 1, "BAD", 0);

    private static final String PROMPT_TEMPLATE =
            "Rate how relevant this product is to the search query, on a 5-point scale.\n" +
            "PERFECT: exactly what the customer searched for.\n" +
            "EXCELLENT: a very strong match, minor differences at most.\n" +
            "GOOD: a reasonable match, same general category/purpose.\n" +
            "FAIR: loosely related, would not fully satisfy the search.\n" +
            "BAD: not relevant to the search at all.\n" +
            "Respond with only the single label word.\n\n" +
            "Query: %s\nProduct: {{PRODUCT}}\nLabel:";

    @Value("${nexarank.search-api.base-url:http://search-api.default.svc.cluster.local/api/v1}")
    private String searchApiBaseUrl;

    @Value("${nexarank.search-api.api-key:searchx-dev-key-2026}")
    private String searchApiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper;
    private final JudgmentRepository judgmentRepository;
    private final LlmConfigService llmConfigService;
    private final LlmAdapterFactory llmAdapterFactory;

    public LlmJudgmentService(RestTemplate restTemplate, ObjectMapper mapper,
                               JudgmentRepository judgmentRepository,
                               LlmConfigService llmConfigService,
                               LlmAdapterFactory llmAdapterFactory) {
        this.restTemplate = restTemplate;
        this.mapper = mapper;
        this.judgmentRepository = judgmentRepository;
        this.llmConfigService = llmConfigService;
        this.llmAdapterFactory = llmAdapterFactory;
    }

    /**
     * Auto-scores the top {@code topN} live results for {@code query} into
     * {@code setId}, upserting each as a PENDING_REVIEW LLM judgment.
     * Returns the saved judgments (already-judged products are re-scored
     * only if still PENDING_REVIEW — an APPROVED judgment, human or LLM, is
     * never silently overwritten by a re-run).
     */
    public List<Judgment> autoScore(String setId, String query, int topN) {
        LlmConfig llmConfig = llmConfigService.getConfig().orElse(null);
        if (llmConfig == null) {
            throw new IllegalStateException("No LLM configured for this project — configure one under LLM Config first");
        }
        LlmPort adapter = llmAdapterFactory.getAdapter(llmConfig);

        List<Map<String, String>> results = fetchResultsWithTitles(query, Math.min(topN, 10));
        List<Judgment> saved = new ArrayList<>();

        for (Map<String, String> result : results) {
            String productId = result.get("productId");
            String title = result.get("title");
            if (productId == null) continue;

            Optional<Judgment> existing = judgmentRepository.findBySetIdAndQueryAndProductId(setId, query, productId);
            if (existing.isPresent() && "APPROVED".equals(existing.get().getStatus())) {
                // Already reviewed (human or LLM-then-approved) — don't silently overwrite.
                continue;
            }

            try {
                String prompt = PROMPT_TEMPLATE.replace("{{PRODUCT}}", title != null ? title : productId);
                String raw = adapter.classify(query, prompt, llmConfig);
                Integer grade = raw == null ? null : GRADE_LABELS.stream()
                        .filter(raw::contains)
                        .map(LABEL_TO_GRADE::get)
                        .findFirst()
                        .orElse(null);

                if (grade == null) {
                    log.warn("LLM judgment scoring unparseable response '{}' for query='{}' product='{}' — skipped", raw, query, productId);
                    continue;
                }

                Judgment judgment = existing.orElseGet(Judgment::new);
                if (judgment.getId() == null) judgment.setId(UUID.randomUUID().toString());
                judgment.setSetId(setId);
                judgment.setQuery(query);
                judgment.setProductId(productId);
                judgment.setProductTitle(title);
                judgment.setGrade(grade);
                judgment.setLlmGrade(grade);
                judgment.setSource("LLM");
                judgment.setStatus("PENDING_REVIEW");
                judgment.setJudgedBy("AI (" + llmConfig.getModel() + ")");
                judgment.setJudgedAt(Instant.now());
                judgment.setReviewedBy(null);
                judgment.setReviewedAt(null);

                saved.add(judgmentRepository.save(judgment));
            } catch (Exception e) {
                log.warn("LLM judgment scoring failed for query='{}' product='{}': {}", query, productId, e.getMessage());
            }
        }
        return saved;
    }

    private List<Map<String, String>> fetchResultsWithTitles(String query, int size) {
        try {
            String url = searchApiBaseUrl + "/search?q=" +
                    URLEncoder.encode(query, StandardCharsets.UTF_8) + "&mode=hybrid&size=" + size;
            var headers = new org.springframework.http.HttpHeaders();
            headers.set("X-API-Key", searchApiKey);
            var entity = new org.springframework.http.HttpEntity<>(headers);
            var response = restTemplate.exchange(url,
                    org.springframework.http.HttpMethod.GET, entity, String.class);
            JsonNode root = mapper.readTree(response.getBody());
            JsonNode hits = root.get("hits");
            List<Map<String, String>> results = new ArrayList<>();
            if (hits != null && hits.isArray()) {
                for (JsonNode h : hits) {
                    Map<String, String> r = new HashMap<>();
                    r.put("productId", h.has("productId") ? h.get("productId").asText() : null);
                    r.put("title", h.has("title") ? h.get("title").asText() : null);
                    results.add(r);
                }
            }
            return results;
        } catch (Exception e) {
            log.warn("Failed to fetch live results for query='{}': {}", query, e.getMessage());
            return List.of();
        }
    }
}
