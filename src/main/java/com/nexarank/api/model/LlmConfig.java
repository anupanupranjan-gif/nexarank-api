// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "llm_config")
public class LlmConfig {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private LlmProvider provider;

    @Column(name = "endpoint", nullable = false)
    private String endpoint;

    @Column(name = "api_key")
    private String apiKey;

    @Column(name = "model", nullable = false)
    private String model;

    @Column(name = "timeout_seconds")
    private int timeoutSeconds = 2;

    /**
     * NR-176: JSON-serialized {@code Map<String,String>} keyed by PROMPT_KEY_*,
     * one map for all admin-configurable prompt templates (rewrite, classification,
     * suggestion, zero_result_recovery, judgment). Replaces the two separate
     * promptTemplate/classificationPromptTemplate TEXT columns from NR-174 —
     * same JSON-column-plus-transient-field pattern as
     * ContentRule.contentPayloadJson/contentPayload and
     * MerchRule.pinnedIdsJson/pinnedIds. Serialization happens in
     * LlmConfigService, not here.
     */
    @Column(name = "prompt_templates", columnDefinition = "TEXT")
    private String promptTemplatesJson;

    @Transient
    private Map<String, String> promptTemplates;

    /**
     * NR-124: optional extra HTTP headers for OPENAI_COMPATIBLE providers that
     * need something beyond standard "Authorization: Bearer {apiKey}" — JSON
     * object string, e.g. {"X-Custom-Header":"value"}. Null/blank for every
     * other provider, including the common case of an OpenAI-compatible
     * provider that only needs Bearer auth.
     */
    @Column(name = "custom_headers", columnDefinition = "TEXT")
    private String customHeaders;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_status")
    private ConnectionStatus lastStatus = ConnectionStatus.UNTESTED;

    @Column(name = "last_status_message")
    private String lastStatusMessage;

    @Column(name = "last_tested_at")
    private Instant lastTestedAt;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    // NR-124: OPENAI_COMPATIBLE covers any provider exposing an OpenAI-shaped
    // /chat/completions API (Groq, Together.ai, Mistral, DeepSeek, etc.) via
    // GenericOpenAiCompatibleLlmAdapter — deliberately a new, distinct value
    // rather than repurposing OPENAI/AZURE_OPENAI, which remain unimplemented
    // named placeholders (see NR-124's Jira comment; not this ticket's scope
    // to build bespoke adapters for them).
    public enum LlmProvider { OLLAMA, OPENAI, AZURE_OPENAI, ANTHROPIC, COHERE, OPENAI_COMPATIBLE }

    public enum ConnectionStatus { UNTESTED, CONNECTED, FAILED }

    // ── NR-176: named keys for every admin-configurable prompt template ────────
    public static final String PROMPT_KEY_REWRITE = "rewrite";
    public static final String PROMPT_KEY_CLASSIFICATION = "classification";
    public static final String PROMPT_KEY_SUGGESTION = "suggestion";
    public static final String PROMPT_KEY_ZERO_RESULT_RECOVERY = "zero_result_recovery";
    public static final String PROMPT_KEY_JUDGMENT = "judgment";

    /**
     * Default text for each prompt key, used whenever promptTemplates has no
     * (or a blank) override for that key. These are the same literal strings
     * that used to be hardcoded as hidden constants on LlmQueryRewriteStage's
     * template (pre-NR-174), LlmQueryClassificationStage (pre-NR-174),
     * AiRuleSuggestionService.callLlmForSynonyms, LlmZeroResultRecoveryService,
     * and LlmJudgmentService — only their storage location changed.
     *
     * - "suggestion" keeps its original two-%s shape: the first %s is the
     *   query, the second is the caller-supplied promptContext clause (e.g.
     *   " and got zero results"). Callers do their own
     *   String.format(template, query, promptContext).
     * - "judgment" keeps its original one-%s-plus-{{PRODUCT}}-marker shape:
     *   %s is the query, {{PRODUCT}} is replaced via String.replace before
     *   the template is handed to LlmPort.classify() (which itself does no
     *   further substitution on this key).
     */
    public static final Map<String, String> DEFAULT_PROMPT_TEMPLATES = Map.of(
            PROMPT_KEY_REWRITE,
            "eCommerce search keywords for: %s\nKeywords (5 words max):",

            PROMPT_KEY_CLASSIFICATION,
            "Classify the eCommerce search intent of the query into exactly one label.\n" +
            "NAVIGATIONAL: user wants a specific product, brand+model, or part/SKU number.\n" +
            "TRANSACTIONAL: user is ready to buy or is comparing price/deals (buy, cheap, deal, best, vs).\n" +
            "CATEGORICAL: user is browsing a general product category, not a specific item.\n" +
            "INFORMATIONAL: broad research query, none of the above.\n" +
            "Respond with only the single label word, nothing else.\n\n" +
            "Query: %s\nLabel:",

            PROMPT_KEY_SUGGESTION,
            "A customer searched for '%s' on an eCommerce site%s. " +
            "Suggest 2-3 alternative search terms or synonyms. " +
            "Reply with ONLY the synonyms separated by commas. No explanation.",

            PROMPT_KEY_ZERO_RESULT_RECOVERY,
            "A customer searched for '%s' on an eCommerce site and got ZERO results. " +
            "Suggest ONE alternative search query that is more likely to return results — " +
            "broaden an overly-specific term, fix a likely typo/misspelling, or use a more " +
            "common synonym. Reply with ONLY the alternative query text. No explanation, no quotes.",

            PROMPT_KEY_JUDGMENT,
            "Rate how relevant this product is to the search query, on a 5-point scale.\n" +
            "PERFECT: exactly what the customer searched for.\n" +
            "EXCELLENT: a very strong match, minor differences at most.\n" +
            "GOOD: a reasonable match, same general category/purpose.\n" +
            "FAIR: loosely related, would not fully satisfy the search.\n" +
            "BAD: not relevant to the search at all.\n" +
            "Respond with only the single label word.\n\n" +
            "Query: %s\nProduct: {{PRODUCT}}\nLabel:"
    );

    /** Returns the configured override for {@code key}, or its default when unset/blank. */
    public String getEffectivePromptTemplate(String key) {
        String override = promptTemplates != null ? promptTemplates.get(key) : null;
        return (override != null && !override.isBlank())
            ? override
            : DEFAULT_PROMPT_TEMPLATES.get(key);
    }

    public String getId()                              { return id; }
    public void setId(String id)                       { this.id = id; }
    public String getTenantId()                        { return tenantId; }
    public void setTenantId(String t)                  { this.tenantId = t; }
    public String getProjectId()                       { return projectId; }
    public void setProjectId(String p)                 { this.projectId = p; }
    public LlmProvider getProvider()                   { return provider; }
    public void setProvider(LlmProvider p)             { this.provider = p; }
    public String getEndpoint()                        { return endpoint; }
    public void setEndpoint(String e)                  { this.endpoint = e; }
    public String getApiKey()                          { return apiKey; }
    public void setApiKey(String k)                    { this.apiKey = k; }
    public String getModel()                           { return model; }
    public void setModel(String m)                     { this.model = m; }
    public int getTimeoutSeconds()                     { return timeoutSeconds; }
    public void setTimeoutSeconds(int t)               { this.timeoutSeconds = t; }
    public String getPromptTemplatesJson()              { return promptTemplatesJson; }
    public void setPromptTemplatesJson(String j)        { this.promptTemplatesJson = j; }
    public Map<String, String> getPromptTemplates()     { return promptTemplates; }
    public void setPromptTemplates(Map<String, String> p) { this.promptTemplates = p; }
    public String getCustomHeaders()                   { return customHeaders; }
    public void setCustomHeaders(String h)             { this.customHeaders = h; }
    public ConnectionStatus getLastStatus()            { return lastStatus; }
    public void setLastStatus(ConnectionStatus s)      { this.lastStatus = s; }
    public String getLastStatusMessage()               { return lastStatusMessage; }
    public void setLastStatusMessage(String m)         { this.lastStatusMessage = m; }
    public Instant getLastTestedAt()                   { return lastTestedAt; }
    public void setLastTestedAt(Instant t)             { this.lastTestedAt = t; }
    public Instant getCreatedAt()                      { return createdAt; }
    public void setCreatedAt(Instant t)                { this.createdAt = t; }
    public Instant getUpdatedAt()                      { return updatedAt; }
    public void setUpdatedAt(Instant t)                { this.updatedAt = t; }
}
