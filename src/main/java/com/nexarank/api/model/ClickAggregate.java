// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "search-click-events")
public class ClickAggregate {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String query;

    @Field(type = FieldType.Keyword)
    private String productId;

    @Field(type = FieldType.Text)
    private String productTitle;

    @Field(type = FieldType.Long)
    private long clickCount;

    @Field(type = FieldType.Long)
    private long impressionCount;

    @Field(type = FieldType.Double)
    private double avgPosition;

    @Field(type = FieldType.Double)
    private double ctr;

    @Field(type = FieldType.Long)
    private long lastClickedAt;

    // Getters
    public String getId() { return id; }
    public String getQuery() { return query; }
    public String getProductId() { return productId; }
    public String getProductTitle() { return productTitle; }
    public long getClickCount() { return clickCount; }
    public long getImpressionCount() { return impressionCount; }
    public double getAvgPosition() { return avgPosition; }
    public double getCtr() { return ctr; }
    public long getLastClickedAt() { return lastClickedAt; }
}
