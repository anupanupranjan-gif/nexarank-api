// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.time.Instant;

/**
 * Stores the customer's search engine connection config.
 * One per NexaRank tenant. Used for:
 * 1. Admin introspection — fetch fields, sample values for rule config UI
 * 2. Rule translation validation — verify rules apply to real fields
 * 3. Solr demo — plug in Solr config, same rules apply
 */
@Document(indexName = "nexarank_engine_config")
@Setting(replicas = 0)
public class SearchEngineConfig {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private EngineType engineType; // ELASTICSEARCH, SOLR, OPENSEARCH

    @Field(type = FieldType.Keyword)
    private String host;

    @Field(type = FieldType.Integer)
    private int port;

    @Field(type = FieldType.Keyword)
    private String scheme; // http or https

    @Field(type = FieldType.Keyword)
    private String indexName; // ES index or Solr collection

    @Field(type = FieldType.Keyword)
    private String username;

    @Field(type = FieldType.Keyword)
    private String password; // stored encrypted in production

    @Field(type = FieldType.Boolean)
    private boolean sslEnabled;

    @Field(type = FieldType.Boolean)
    private boolean sslVerify; // false for self-signed certs

    @Field(type = FieldType.Keyword)
    private ConnectionStatus lastStatus; // CONNECTED, FAILED, UNTESTED

    @Field(type = FieldType.Text)
    private String lastStatusMessage;

    @Field(type = FieldType.Date)
    private Instant lastTestedAt;

    @Field(type = FieldType.Date)
    private Instant createdAt;

    @Field(type = FieldType.Date)
    private Instant updatedAt;

    public enum EngineType {
        ELASTICSEARCH, SOLR, OPENSEARCH
    }

    public enum ConnectionStatus {
        CONNECTED, FAILED, UNTESTED
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public EngineType getEngineType() { return engineType; }
    public void setEngineType(EngineType engineType) { this.engineType = engineType; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

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
    public void setLastStatusMessage(String msg) { this.lastStatusMessage = msg; }

    public Instant getLastTestedAt() { return lastTestedAt; }
    public void setLastTestedAt(Instant t) { this.lastTestedAt = t; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public String getConnectionUrl() {
        return scheme + "://" + host + ":" + port;
    }
}
