// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarank.api.model.LlmConfig;
import com.nexarank.api.port.LlmPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Ollama LLM adapter.
 * Uses HttpURLConnection — not java.net.http.HttpClient — due to Java 25
 * AArch64 SSL handling differences (same reason as ElasticsearchAdapter).
 */
@Component
public class OllamaLlmAdapter implements LlmPort {

    private static final Logger log = LoggerFactory.getLogger(OllamaLlmAdapter.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public LlmConfig.LlmProvider supportedProvider() {
        return LlmConfig.LlmProvider.OLLAMA;
    }

    @Override
    public boolean testConnection(LlmConfig config) {
        try {
            String url = config.getEndpoint() + "/api/tags";
            String response = get(url, config);
            boolean ok = response != null && response.contains("models");
            log.info("Ollama connection test to {}: {}", config.getEndpoint(), ok ? "OK" : "FAILED");
            return ok;
        } catch (Exception e) {
            log.warn("Ollama connection test failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String rewrite(String query, String promptTemplate, LlmConfig config) {
        try {
            String prompt = String.format(promptTemplate, query);

            String body = mapper.writeValueAsString(Map.of(
                "model",  config.getModel(),
                "prompt", prompt,
                "stream", false,
                "options", Map.of(
                    "temperature", 0.3,
                    "num_predict", 60
                )
            ));

            String url = config.getEndpoint() + "/api/generate";
            String response = post(url, body, config);
            if (response == null || response.isBlank()) {
                log.warn("Ollama empty response for query='{}', using original", query);
                return query;
            }

            JsonNode json = mapper.readTree(response);
            String rewritten = json.path("response").asText("").trim();

            if (rewritten.isBlank() || rewritten.length() < 3) {
                log.warn("Ollama rewrite too short for query='{}', using original", query);
                return query;
            }

// Take only the first line — small models tend to over-generate
            if (rewritten.contains("\n")) {
                rewritten = rewritten.substring(0, rewritten.indexOf("\n")).trim();
            }

// Strip "original query ->" prefix if model echoed it back
            if (rewritten.contains("->")) {
                rewritten = rewritten.substring(rewritten.lastIndexOf("->") + 2).trim();
            }

// Strip everything up to and including the last colon
// e.g. "Okay, here's keywords: snow tires ice chains" -> "snow tires ice chains"
            if (rewritten.contains(":")) {
                String afterColon = rewritten.substring(rewritten.lastIndexOf(":") + 1).trim();
                if (!afterColon.isBlank() && afterColon.length() >= 3) {
                    rewritten = afterColon;
                }
            }

// Strip markdown bold markers and quotes
            rewritten = rewritten.replaceAll("\\*\\*", "").replaceAll("[\"']", "").trim();

// If still nothing useful, fall back to original
            if (rewritten.isBlank() || rewritten.length() < 3) {
                log.warn("Ollama rewrite empty after cleanup for query='{}', using original", query);
                return query;
            }

// Truncate if still too long
            if (rewritten.length() > 200) rewritten = rewritten.substring(0, 200);

            log.info("Ollama rewrite '{}' -> '{}'", query, rewritten);
            return rewritten;

        } catch (Exception e) {
            log.warn("Ollama rewrite failed for query='{}': {}, using original", query, e.getMessage());
            return query;
        }
    }

    // ── HTTP helpers (HttpURLConnection, same pattern as ElasticsearchAdapter) ──

    private String get(String url, LlmConfig config) throws Exception {
        return execute("GET", url, null, config);
    }

    private String post(String url, String body, LlmConfig config) throws Exception {
        return execute("POST", url, body, config);
    }

    private String execute(String method, String url, String body,
                            LlmConfig config) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(config.getTimeoutSeconds() * 1000);

        // API key header — Ollama doesn't use it but OpenAI-compatible providers do
        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            conn.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
        }

        if (body != null) {
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }

        int status = conn.getResponseCode();
        var is = status < 400 ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return "";
        try (var scanner = new java.util.Scanner(is, StandardCharsets.UTF_8)) {
            return scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
        }
    }
}
