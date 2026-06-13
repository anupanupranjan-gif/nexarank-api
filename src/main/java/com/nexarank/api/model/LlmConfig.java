// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.model;

import jakarta.persistence.*;
import java.time.Instant;

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

    @Column(name = "prompt_template", columnDefinition = "TEXT")
    private String promptTemplate;

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

    public enum LlmProvider { OLLAMA, OPENAI, AZURE_OPENAI, ANTHROPIC, COHERE }

    public enum ConnectionStatus { UNTESTED, CONNECTED, FAILED }

    // ── Default prompt template used if none configured per project ───────────
    public static final String DEFAULT_PROMPT_TEMPLATE =
            "eCommerce search keywords for: %s\nKeywords (5 words max):";

    public String getEffectivePromptTemplate() {
        return (promptTemplate != null && !promptTemplate.isBlank())
            ? promptTemplate
            : DEFAULT_PROMPT_TEMPLATE;
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
    public String getPromptTemplate()                  { return promptTemplate; }
    public void setPromptTemplate(String p)            { this.promptTemplate = p; }
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
