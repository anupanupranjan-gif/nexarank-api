// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarank.api.model.SearchEngineConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Phase 23 / NR-37 v2: fetch live facet values from Elasticsearch.
 * Uses the stored engine_config connection details.
 * Called by the rule builder UI to populate the facet value picker.
 */
@Service
public class FacetValuesService {

    private static final Logger log = LoggerFactory.getLogger(FacetValuesService.class);
    private final SearchEngineConfigService configService;
    private final ObjectMapper objectMapper;

    public FacetValuesService(SearchEngineConfigService configService, ObjectMapper objectMapper) {
        this.configService = configService;
        this.objectMapper = objectMapper;
    }

    /**
     * Fetch top facet values for a field from Elasticsearch.
     *
     * @param facetField  the ES field name e.g. "category", "brand"
     * @param query       optional query to scope results (null or blank = match all)
     * @param size        number of top values to return (default 20)
     * @return list of {value, count} maps sorted by doc count desc
     */
    public List<Map<String, Object>> getFacetValues(String facetField, String query, int size) {
        SearchEngineConfig config = configService.getConfig().orElse(null);
        if (config == null) {
            log.warn("No engine config found, cannot fetch facet values");
            return List.of();
        }

        try {
            String esQuery = (query == null || query.isBlank() || query.equals("*"))
                    ? "{\"match_all\":{}}"
                    : "{\"multi_match\":{\"query\":\"" + escapeJson(query) +
                      "\",\"fields\":[\"name\",\"description\",\"category\",\"brand\"]}}";

            String body = String.format("""
                    {
                      "size": 0,
                      "query": %s,
                      "aggs": {
                        "facet_values": {
                          "terms": {
                            "field": "%s",
                            "size": %d,
                            "order": {"_count": "desc"}
                          }
                        }
                      }
                    }
                    """, esQuery, facetField, size);

            String urlStr = config.getScheme() + "://" + config.getHost() + ":" +
                    config.getPort() + "/" + config.getIndexName() + "/_search";

            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("Content-Type", "application/json");

            if (config.getUsername() != null && !config.getUsername().isBlank()) {
                String auth = Base64.getEncoder().encodeToString(
                        (config.getUsername() + ":" + config.getPassword())
                                .getBytes(StandardCharsets.UTF_8));
                conn.setRequestProperty("Authorization", "Basic " + auth);
            }

            // Skip SSL verification if configured
            if (!config.isSslVerify() && conn instanceof javax.net.ssl.HttpsURLConnection https) {
                https.setSSLSocketFactory(getTrustAllSslContext().getSocketFactory());
                https.setHostnameVerifier((h, s) -> true);
            }

            conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));

            int status = conn.getResponseCode();
            if (status != 200) {
                log.warn("ES facet values request returned {}", status);
                return List.of();
            }

            byte[] resp = conn.getInputStream().readAllBytes();
            JsonNode root = objectMapper.readTree(resp);
            JsonNode buckets = root.path("aggregations").path("facet_values").path("buckets");

            List<Map<String, Object>> results = new ArrayList<>();
            for (JsonNode bucket : buckets) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("value", bucket.path("key").asText());
                entry.put("count", bucket.path("doc_count").asLong());
                results.add(entry);
            }
            return results;

        } catch (Exception e) {
            log.error("Failed to fetch facet values for field='{}' query='{}': {}",
                    facetField, query, e.getMessage());
            return List.of();
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private javax.net.ssl.SSLContext getTrustAllSslContext() {
        try {
            javax.net.ssl.SSLContext ctx = javax.net.ssl.SSLContext.getInstance("TLS");
            ctx.init(null, new javax.net.ssl.TrustManager[]{
                new javax.net.ssl.X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                    public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
                }
            }, null);
            return ctx;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create trust-all SSL context", e);
        }
    }
}
