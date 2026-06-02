// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarank.api.model.*;
import com.nexarank.api.port.SearchEnginePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import java.util.*;

@Component
public class ElasticsearchAdapter implements SearchEnginePort {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchAdapter.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public SearchEngineConfig.EngineType supportedEngine() {
        return SearchEngineConfig.EngineType.ELASTICSEARCH;
    }

    @Override
    public boolean testConnection(SearchEngineConfig config) {
        try {
            String url = config.getConnectionUrl() + "/_cluster/health";
            String res = get(url, config);
            boolean ok = res != null && res.contains("status");
            log.info("ES connection test to {}: {}", config.getConnectionUrl(), ok ? "OK" : "FAILED");
            return ok;
        } catch (Exception e) {
            log.warn("ES connection test failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public List<SearchField> getFields(SearchEngineConfig config) {
        try {
            String url = config.getConnectionUrl() + "/" + config.getIndexName() + "/_mapping";
            String resBody = get(url, config);
            if (resBody == null || resBody.isBlank()) return List.of();

            JsonNode root = mapper.readTree(resBody);
            List<SearchField> fields = new ArrayList<>();

            // Navigate: indexName -> mappings -> properties
            JsonNode mappings = root.fields().hasNext()
                ? root.fields().next().getValue().path("mappings").path("properties")
                : root.path(config.getIndexName()).path("mappings").path("properties");

            if (mappings.isMissingNode()) {
                log.warn("No mappings found for index {}", config.getIndexName());
                return List.of();
            }

            mappings.fields().forEachRemaining(entry -> {
                String name = entry.getKey();
                JsonNode fieldDef = entry.getValue();
                String type = fieldDef.path("type").asText("object");

                // Skip internal fields and vector fields
                if (name.startsWith("_") || type.equals("dense_vector")) return;

                SearchField field = new SearchField();
                field.setName(name);
                field.setType(type);
                field.setIndexed(true);
                field.setStored(true);
                field.setFacetable(type.equals("keyword") ||
                                   type.equals("boolean") ||
                                   type.equals("integer") ||
                                   type.equals("float") ||
                                   type.equals("double"));
                field.setSortable(type.equals("keyword") ||
                                  type.equals("integer") ||
                                  type.equals("float") ||
                                  type.equals("date"));
                fields.add(field);
            });

            log.info("Fetched {} fields from ES index {}", fields.size(), config.getIndexName());
            return fields;

        } catch (Exception e) {
            log.error("Failed to get fields from ES", e);
            return List.of();
        }
    }

    @Override
    public List<String> getFieldValues(String fieldName, SearchEngineConfig config) {
        try {
            String url = config.getConnectionUrl() + "/" + config.getIndexName() + "/_search";
            String body = """
                {
                  "size": 0,
                  "aggs": {
                    "values": {
                      "terms": { "field": "%s", "size": 20 }
                    }
                  }
                }
                """.formatted(fieldName);

            String resBody = post(url, body, config);
            if (resBody == null || resBody.isBlank()) return List.of();

            JsonNode root = mapper.readTree(resBody);
            List<String> values = new ArrayList<>();
            root.path("aggregations").path("values").path("buckets")
                .forEach(b -> values.add(b.path("key").asText()));
            return values;

        } catch (Exception e) {
            log.error("Failed to get field values for {}", fieldName, e);
            return List.of();
        }
    }

    @Override
    public EnrichedQuery translateRules(String query, List<MerchRule> rules,
                                        SearchEngineConfig config) {
        EnrichedQuery result = new EnrichedQuery();
        result.setOriginalQuery(query);
        result.setExpandedQuery(query);
        result.setEngineType("ELASTICSEARCH");

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
                        log.debug("ES BOOST: {}={} x{}", rule.getBoostField(),
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
                        String expanded = query + " " + String.join(" ", rule.getSynonyms());
                        result.setExpandedQuery(expanded);
                        appliedRules.add(rule.getId());
                        log.debug("ES SYNONYM expanded '{}' -> '{}'", query, expanded);
                    }
                }
            }
        }

        result.setBoosts(boosts);
        result.setPins(pins);
        result.setBuries(buries);
        result.setAppliedRules(appliedRules);

        // Build ES-specific DSL for convenience
        result.setEngineDsl(buildEsDsl(result));

        return result;
    }

    /**
     * Builds the ES-specific query DSL as a Map.
     * Customer can inject this directly into their ES query.
     */
    private Map<String, Object> buildEsDsl(EnrichedQuery eq) {
        Map<String, Object> dsl = new LinkedHashMap<>();
        dsl.put("expandedQuery", eq.getExpandedQuery());

        // Function score functions for boosts and buries
        List<Map<String, Object>> functions = new ArrayList<>();

        for (EnrichedQuery.BoostInstruction boost : eq.getBoosts()) {
            functions.add(Map.of(
                "filter", Map.of("term", Map.of(boost.field(), boost.value())),
                "weight", boost.factor()
            ));
        }
        for (EnrichedQuery.BuryInstruction bury : eq.getBuries()) {
            functions.add(Map.of(
                "filter", Map.of("term", Map.of(bury.field(), bury.value())),
                "weight", bury.factor()
            ));
        }
        if (!functions.isEmpty()) {
            dsl.put("functionScoreFunctions", functions);
        }

        // Pinned IDs
        if (!eq.getPins().isEmpty()) {
            List<String> pinnedIds = eq.getPins().stream()
                .sorted(Comparator.comparingInt(EnrichedQuery.PinInstruction::position))
                .map(EnrichedQuery.PinInstruction::productId)
                .toList();
            dsl.put("pinnedIds", pinnedIds);
        }

        return dsl;
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────
    // Uses HttpURLConnection instead of java.net.http.HttpClient
    // because HttpClient ignores SSLParameters hostname verification in Java 25

    private String get(String url, SearchEngineConfig config) throws Exception {
        return execute("GET", url, null, config);
    }

    private String post(String url, String body, SearchEngineConfig config) throws Exception {
        return execute("POST", url, body, config);
    }

    private String execute(String method, String url, String body,
                            SearchEngineConfig config) throws Exception {
        if (config.isSslEnabled() && !config.isSslVerify()) {
            disableHostnameVerification();
        }
        java.net.URL u = new java.net.URL(url);
        javax.net.ssl.HttpsURLConnection conn = config.isSslEnabled()
            ? (javax.net.ssl.HttpsURLConnection) u.openConnection()
            : null;
        java.net.HttpURLConnection http = conn != null ? conn
            : (java.net.HttpURLConnection) u.openConnection();

        if (conn != null && !config.isSslVerify()) {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, null);
            conn.setSSLSocketFactory(ctx.getSocketFactory());
            conn.setHostnameVerifier((h, s) -> true);
        }

        http.setRequestMethod(method);
        http.setRequestProperty("Authorization", basicAuth(config));
        http.setRequestProperty("Content-Type", "application/json");
        http.setConnectTimeout(5000);
        http.setReadTimeout(5000);

        if (body != null) {
            http.setDoOutput(true);
            try (java.io.OutputStream os = http.getOutputStream()) {
                os.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }

        int status = http.getResponseCode();
        java.io.InputStream is = status < 400 ? http.getInputStream() : http.getErrorStream();
        if (is == null) return "";
        try (java.util.Scanner scanner = new java.util.Scanner(is,
                java.nio.charset.StandardCharsets.UTF_8)) {
            return scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
        }
    }

    private String basicAuth(SearchEngineConfig config) {
        String creds = config.getUsername() + ":" + config.getPassword();
        return "Basic " + Base64.getEncoder().encodeToString(creds.getBytes());
    }

    private void disableHostnameVerification() {
        javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier((h, s) -> true);
    }
}
