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
import java.util.List;
import java.util.Map;

/**
 * NR-124 — one adapter for the whole class of providers that expose an
 * OpenAI-shaped chat completions API (Groq, Together.ai, Mistral, DeepSeek,
 * Azure OpenAI, OpenAI itself, etc.) via config alone (endpoint/apiKey/model),
 * rather than a bespoke adapter class per vendor. Does NOT cover Claude —
 * Claude's tool-use mechanism for structured output (needed for NR-78) isn't
 * the same shape as OpenAI's function-calling API and needs its own adapter
 * if/when that's built — and does not cover Bedrock, which isn't
 * OpenAI-compatible at all.
 *
 * config.getEndpoint() is expected to be the provider's OpenAI-compatible
 * base URL INCLUDING any version segment the provider uses (e.g.
 * "https://api.groq.com/openai/v1", "https://api.together.xyz/v1") — this
 * adapter appends "/chat/completions" and "/models" to it, exactly like
 * OpenAI's own client libraries expect callers to configure baseUrl.
 *
 * Uses HttpURLConnection, same reasoning as OllamaLlmAdapter (Java 25
 * AArch64 SSL handling differences with java.net.http.HttpClient) — this
 * one actually calls real HTTPS third-party endpoints, so it's the adapter
 * most likely to hit that bug if HttpClient were used instead.
 */
@Component
public class GenericOpenAiCompatibleLlmAdapter implements LlmPort {

    private static final Logger log = LoggerFactory.getLogger(GenericOpenAiCompatibleLlmAdapter.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public LlmConfig.LlmProvider supportedProvider() {
        return LlmConfig.LlmProvider.OPENAI_COMPATIBLE;
    }

    @Override
    public boolean testConnection(LlmConfig config) {
        try {
            String url = config.getEndpoint() + "/models";
            String response = execute("GET", url, null, config);
            // A 401/403 still returns a JSON body (not an exception) via the
            // error-stream fallback below — checking for "data"/"id" (the
            // OpenAI models-list shape) rather than just "did we get a
            // response" avoids reporting a bad API key as a successful test.
            boolean ok = response != null && (response.contains("\"data\"") || response.contains("\"id\""));
            log.info("OpenAI-compatible connection test to {}: {}", config.getEndpoint(), ok ? "OK" : "FAILED");
            return ok;
        } catch (Exception e) {
            log.warn("OpenAI-compatible connection test failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String rewrite(String query, String promptTemplate, LlmConfig config) {
        String prompt = String.format(promptTemplate, query);
        String content = chatComplete(prompt, config, 0.3, 60);
        if (content == null) {
            log.warn("OpenAI-compatible rewrite empty/failed for query='{}', using original", query);
            return query;
        }

        String rewritten = content.trim();
        if (rewritten.contains("\n")) {
            rewritten = rewritten.substring(0, rewritten.indexOf("\n")).trim();
        }
        rewritten = rewritten.replaceAll("\\*\\*", "").replaceAll("[\"']", "").trim();
        if (rewritten.length() > 200) rewritten = rewritten.substring(0, 200);

        if (rewritten.isBlank() || rewritten.length() < 3) {
            log.warn("OpenAI-compatible rewrite unusable for query='{}', using original", query);
            return query;
        }
        log.info("OpenAI-compatible rewrite '{}' -> '{}'", query, rewritten);
        return rewritten;
    }

    @Override
    public String classify(String query, String promptTemplate, LlmConfig config) {
        String prompt = String.format(promptTemplate, query);
        String content = chatComplete(prompt, config, 0.1, 15);
        if (content == null) return null;

        String raw = content.trim();
        if (raw.contains("\n")) raw = raw.substring(0, raw.indexOf("\n")).trim();
        raw = raw.replaceAll("\\*\\*", "").replaceAll("[\"']", "").trim().toUpperCase();
        return raw.isBlank() ? null : raw;
    }

    /** Shared chat-completions call backing both rewrite() and classify(). */
    private String chatComplete(String prompt, LlmConfig config, double temperature, int maxTokens) {
        try {
            Map<String, Object> body = Map.of(
                "model", config.getModel(),
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "temperature", temperature,
                "max_tokens", maxTokens
            );
            String url = config.getEndpoint() + "/chat/completions";
            String response = execute("POST", url, mapper.writeValueAsString(body), config);
            if (response == null || response.isBlank()) return null;

            JsonNode json = mapper.readTree(response);
            JsonNode choices = json.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                log.warn("OpenAI-compatible response had no choices: {}", response);
                return null;
            }
            String content = choices.get(0).path("message").path("content").asText("");
            return content.isBlank() ? null : content;
        } catch (Exception e) {
            log.warn("OpenAI-compatible chat completion failed: {}", e.getMessage());
            return null;
        }
    }

    // ── HTTP helper (HttpURLConnection, same pattern as OllamaLlmAdapter) ───────

    private String execute(String method, String url, String body, LlmConfig config) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(config.getTimeoutSeconds() * 1000);

        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            conn.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
        }
        applyCustomHeaders(conn, config);

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

    @SuppressWarnings("unchecked")
    private void applyCustomHeaders(HttpURLConnection conn, LlmConfig config) {
        String raw = config.getCustomHeaders();
        if (raw == null || raw.isBlank()) return;
        try {
            Map<String, Object> headers = mapper.readValue(raw, Map.class);
            headers.forEach((k, v) -> conn.setRequestProperty(k, String.valueOf(v)));
        } catch (Exception e) {
            log.warn("Ignoring unparseable customHeaders for LLM config {}: {}", config.getId(), e.getMessage());
        }
    }
}
