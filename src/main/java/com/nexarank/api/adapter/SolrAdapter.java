// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarank.api.model.*;
import com.nexarank.api.port.SearchEnginePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Component
public class SolrAdapter implements SearchEnginePort {

    private static final Logger log = LoggerFactory.getLogger(SolrAdapter.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public SearchEngineConfig.EngineType supportedEngine() {
        return SearchEngineConfig.EngineType.SOLR;
    }

    @Override
    public boolean testConnection(SearchEngineConfig config) {
        try {
            // Solr admin ping endpoint
            String url = config.getConnectionUrl() + "/solr/" +
                         config.getIndexName() + "/admin/ping?wt=json";
            HttpResponse<String> res = get(url, config);
            boolean ok = res.statusCode() == 200;
            log.info("Solr connection test to {}: {}", config.getConnectionUrl(),
                ok ? "OK" : "FAILED (status " + res.statusCode() + ")");
            return ok;
        } catch (Exception e) {
            log.warn("Solr connection test failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public List<SearchField> getFields(SearchEngineConfig config) {
        try {
            // Solr schema fields endpoint — more reliable than Luke for dynamic fields
            String url = config.getConnectionUrl() + "/solr/" +
                         config.getIndexName() + "/schema/fields?wt=json";
            HttpResponse<String> res = get(url, config);
            if (res.statusCode() != 200) return List.of();

            JsonNode root = mapper.readTree(res.body());
            JsonNode fields = root.path("fields");
            List<SearchField> result = new ArrayList<>();

            for (JsonNode fieldDef : fields) {
                String name = fieldDef.path("name").asText();

                // Skip internal Solr fields
                if (name.startsWith("_") || name.equals("id")) continue;

                String type = fieldDef.path("type").asText("string");
                boolean indexed = fieldDef.path("indexed").asBoolean(true);
                boolean stored = fieldDef.path("stored").asBoolean(true);
                boolean multiValued = fieldDef.path("multiValued").asBoolean(false);

                SearchField field = new SearchField();
                field.setName(name);
                field.setType(mapSolrType(type));
                field.setIndexed(indexed);
                field.setStored(stored);
                field.setFacetable(!multiValued &&
                                   (type.contains("string") || type.contains("int") ||
                                    type.contains("bool") || type.contains("plong")));
                field.setSortable(stored &&
                                  (type.contains("string") || type.contains("int") ||
                                   type.contains("date") || type.contains("plong")));
                result.add(field);
            }

            log.info("Fetched {} fields from Solr collection {}",
                result.size(), config.getIndexName());
            return result;

        } catch (Exception e) {
            log.error("Failed to get fields from Solr", e);
            return List.of();
        }
    }

    @Override
    public List<String> getFieldValues(String fieldName, SearchEngineConfig config) {
        try {
            // Solr facet query to get distinct values
            String url = config.getConnectionUrl() + "/solr/" + config.getIndexName() +
                "/select?q=*:*&rows=0&wt=json" +
                "&facet=true&facet.field=" + fieldName + "&facet.limit=20";
            HttpResponse<String> res = get(url, config);
            if (res.statusCode() != 200) return List.of();

            JsonNode root = mapper.readTree(res.body());
            JsonNode facetCounts = root
                .path("facet_counts")
                .path("facet_fields")
                .path(fieldName);

            List<String> values = new ArrayList<>();
            // Solr returns alternating value/count pairs in an array
            boolean isValue = true;
            for (JsonNode node : facetCounts) {
                if (isValue) values.add(node.asText());
                isValue = !isValue;
            }
            return values;

        } catch (Exception e) {
            log.error("Failed to get Solr field values for {}", fieldName, e);
            return List.of();
        }
    }

    @Override
    public EnrichedQuery translateRules(String query, List<MerchRule> rules,
                                         SearchEngineConfig config) {
        EnrichedQuery result = new EnrichedQuery();
        result.setOriginalQuery(query);
        result.setExpandedQuery(query);
        result.setEngineType("SOLR");

        List<EnrichedQuery.BoostInstruction> boosts = new ArrayList<>();
        List<EnrichedQuery.PinInstruction> pins = new ArrayList<>();
        List<EnrichedQuery.BuryInstruction> buries = new ArrayList<>();
        List<String> appliedRules = new ArrayList<>();

        for (MerchRule rule : rules) {
            switch (rule.getType()) {
                case BOOST -> {
                    if (rule.getBoostField() != null && rule.getBoostValue() != null) {
                        float factor = rule.getBoostFactor() != null ? rule.getBoostFactor() : 1.5f;
                        boosts.add(new EnrichedQuery.BoostInstruction(
                            rule.getBoostField(), rule.getBoostValue(), factor));
                        appliedRules.add(rule.getId());
                        log.debug("Solr BOOST: {}={} ^{}", rule.getBoostField(),
                            rule.getBoostValue(), factor);
                    }
                }
                case BURY -> {
                    if (rule.getBoostField() != null && rule.getBoostValue() != null) {
                        float factor = rule.getBoostFactor() != null ? rule.getBoostFactor() : 0.1f;
                        buries.add(new EnrichedQuery.BuryInstruction(
                            rule.getBoostField(), rule.getBoostValue(), factor));
                        appliedRules.add(rule.getId());
                    }
                }
                case PIN -> {
                    if (rule.getPinnedIds() != null) {
                        for (int i = 0; i < rule.getPinnedIds().size(); i++) {
                            pins.add(new EnrichedQuery.PinInstruction(
                                rule.getPinnedIds().get(i), i + 1));
                        }
                        appliedRules.add(rule.getId());
                    }
                }
                case SYNONYM -> {
                    if (rule.getSynonyms() != null) {
                        boolean oneWay = rule.getSynonymDirection() ==
                                MerchRule.SynonymDirection.ONE_WAY;
                        if (oneWay) {
                            result.setExpandedQuery(String.join(" ", rule.getSynonyms()));
                            log.debug("Solr SYNONYM ONE_WAY '{}' -> '{}'",
                                    query, result.getExpandedQuery());
                        } else {
                            result.setExpandedQuery(query + " " +
                                    String.join(" ", rule.getSynonyms()));
                            log.debug("Solr SYNONYM TWO_WAY '{}' -> '{}'",
                                    query, result.getExpandedQuery());
                        }
                        appliedRules.add(rule.getId());
                    }
                }
            }
        }

        result.setBoosts(boosts);
        result.setPins(pins);
        result.setBuries(buries);
        result.setAppliedRules(appliedRules);

        // Build Solr-specific DSL
        result.setEngineDsl(buildSolrDsl(result));

        return result;
    }

    /**
     * Builds Solr-specific query parameters as a Map.
     * Customer appends these to their Solr query.
     *
     * BOOST → bq (boost query): brand:Duracell^2.5
     * BURY  → bq with low factor: inStock:false^0.1
     * PIN   → Solr Elevation Component (elevateIds list)
     * SYNONYM → expanded query string
     */
    private Map<String, Object> buildSolrDsl(EnrichedQuery eq) {
        Map<String, Object> dsl = new LinkedHashMap<>();
        dsl.put("expandedQuery", eq.getExpandedQuery());

        // Boost queries — Solr bq parameter
        List<String> boostQueries = new ArrayList<>();
        for (EnrichedQuery.BoostInstruction boost : eq.getBoosts()) {
            boostQueries.add(boost.field() + ":" + boost.value() + "^" + boost.factor());
        }
        for (EnrichedQuery.BuryInstruction bury : eq.getBuries()) {
            boostQueries.add(bury.field() + ":" + bury.value() + "^" + bury.factor());
        }
        if (!boostQueries.isEmpty()) {
            dsl.put("bq", boostQueries); // append each as &bq=...
        }

        // Elevation — Solr Elevate Component elevated IDs
        if (!eq.getPins().isEmpty()) {
            List<String> elevatedIds = eq.getPins().stream()
                .sorted(Comparator.comparingInt(EnrichedQuery.PinInstruction::position))
                .map(EnrichedQuery.PinInstruction::productId)
                .toList();
            dsl.put("elevateIds", elevatedIds);
            dsl.put("forceElevation", true);
        }

        return dsl;
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private HttpResponse<String> get(String url, SearchEngineConfig config) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(5))
            .GET();

        // Add basic auth if credentials provided
        if (config.getUsername() != null && !config.getUsername().isBlank()) {
            String creds = config.getUsername() + ":" + config.getPassword();
            builder.header("Authorization", "Basic " +
                Base64.getEncoder().encodeToString(creds.getBytes()));
        }

        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build()
            .send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String mapSolrType(String solrType) {
        if (solrType == null) return "string";
        String t = solrType.toLowerCase();
        if (t.contains("int") || t.contains("long")) return "integer";
        if (t.contains("float") || t.contains("double")) return "float";
        if (t.contains("bool")) return "boolean";
        if (t.contains("date")) return "date";
        if (t.contains("text")) return "text";
        return "keyword";
    }
}
