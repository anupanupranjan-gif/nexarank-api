// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.model;

public class ClickAggregate {
    private String id;
    private String query;
    private String productId;
    private String productTitle;
    private long clickCount;
    private long impressionCount;
    private double avgPosition;
    private double ctr;
    private long lastClickedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public String getProductTitle() { return productTitle; }
    public void setProductTitle(String productTitle) { this.productTitle = productTitle; }
    public long getClickCount() { return clickCount; }
    public void setClickCount(long clickCount) { this.clickCount = clickCount; }
    public long getImpressionCount() { return impressionCount; }
    public void setImpressionCount(long impressionCount) { this.impressionCount = impressionCount; }
    public double getAvgPosition() { return avgPosition; }
    public void setAvgPosition(double avgPosition) { this.avgPosition = avgPosition; }
    public double getCtr() { return ctr; }
    public void setCtr(double ctr) { this.ctr = ctr; }
    public long getLastClickedAt() { return lastClickedAt; }
    public void setLastClickedAt(long lastClickedAt) { this.lastClickedAt = lastClickedAt; }
}
