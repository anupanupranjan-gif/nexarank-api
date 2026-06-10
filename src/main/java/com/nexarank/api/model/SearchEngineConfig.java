// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "engine_config")
public class SearchEngineConfig {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "engine_type", nullable = false)
    private EngineType engineType;

    @Column
    private String host;

    @Column
    private Integer port;

    @Column
    private String scheme = "https";

    @Column(name = "index_name")
    private String indexName;

    @Column
    private String username;

    @Column
    private String password;

    @Column(name = "ssl_enabled")
    private boolean sslEnabled = true;

    @Column(name = "ssl_verify")
    private boolean sslVerify = false;

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

    public enum EngineType { ELASTICSEARCH, SOLR, OPENSEARCH }

    public enum ConnectionStatus { UNTESTED, CONNECTED, FAILED }

    public String getConnectionUrl() {
        if (host == null) return null;
        return (scheme != null ? scheme : "https") + "://" + host + ":" + (port != null ? port : 9200);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public EngineType getEngineType() { return engineType; }
    public void setEngineType(EngineType engineType) { this.engineType = engineType; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
    public String getScheme() { return scheme; }
    public void setScheme(String scheme) { this.scheme = scheme; }
    public String getIndexName() { return indexName; }
    public void setIndexName(String indexName) { this.indexName = indexName; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public boolean isSslEnabled() { return sslEnabled; }
    public void setSslEnabled(boolean sslEnabled) { this.sslEnabled = sslEnabled; }
    public boolean isSslVerify() { return sslVerify; }
    public void setSslVerify(boolean sslVerify) { this.sslVerify = sslVerify; }
    public ConnectionStatus getLastStatus() { return lastStatus; }
    public void setLastStatus(ConnectionStatus lastStatus) { this.lastStatus = lastStatus; }
    public String getLastStatusMessage() { return lastStatusMessage; }
    public void setLastStatusMessage(String lastStatusMessage) { this.lastStatusMessage = lastStatusMessage; }
    public Instant getLastTestedAt() { return lastTestedAt; }
    public void setLastTestedAt(Instant lastTestedAt) { this.lastTestedAt = lastTestedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
