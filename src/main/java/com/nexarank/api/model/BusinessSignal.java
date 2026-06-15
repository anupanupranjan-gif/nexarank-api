// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "business_signals")
public class BusinessSignal {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type", nullable = false)
    private SignalType signalType;

    @Column
    private String value;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Column
    private String source;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public enum SignalType {
        MARGIN_LOW,    // low margin — bury candidate
        OUT_OF_STOCK,  // out of stock — bury candidate
        PROMOTED,      // brand/promotional agreement — boost candidate
        SEASONAL       // seasonal relevance window
    }

    public boolean isActive() {
        Instant now = Instant.now();
        if (validFrom != null && now.isBefore(validFrom)) return false;
        if (validTo != null && now.isAfter(validTo)) return false;
        return true;
    }

    public String getId()                          { return id; }
    public void setId(String id)                   { this.id = id; }
    public String getTenantId()                    { return tenantId; }
    public void setTenantId(String t)              { this.tenantId = t; }
    public String getProjectId()                   { return projectId; }
    public void setProjectId(String p)             { this.projectId = p; }
    public String getProductId()                   { return productId; }
    public void setProductId(String p)             { this.productId = p; }
    public SignalType getSignalType()              { return signalType; }
    public void setSignalType(SignalType s)        { this.signalType = s; }
    public String getValue()                       { return value; }
    public void setValue(String v)                 { this.value = v; }
    public Instant getValidFrom()                  { return validFrom; }
    public void setValidFrom(Instant t)            { this.validFrom = t; }
    public Instant getValidTo()                    { return validTo; }
    public void setValidTo(Instant t)              { this.validTo = t; }
    public String getSource()                      { return source; }
    public void setSource(String s)                { this.source = s; }
    public Instant getCreatedAt()                  { return createdAt; }
    public void setCreatedAt(Instant t)            { this.createdAt = t; }
    public Instant getUpdatedAt()                  { return updatedAt; }
    public void setUpdatedAt(Instant t)            { this.updatedAt = t; }
}
