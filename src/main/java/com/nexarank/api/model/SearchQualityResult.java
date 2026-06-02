// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.model;

import java.util.Map;

public class SearchQualityResult {

    private String runId;
    private long evaluatedAt;
    private int totalQueries;
    private int evaluatedQueries;

    private double ndcg5;
    private double ndcg10;
    private double mrr10;

    private Map<String, IntentMetrics> byIntent;
    private Map<String, ModeMetrics> byMode;

    public record IntentMetrics(
        String intent,
        int queryCount,
        double ndcg10,
        double mrr10
    ) {}

    public record ModeMetrics(
        String mode,
        double ndcg5,
        double ndcg10,
        double mrr10,
        double ndcg10LiftVsBaseline
    ) {}

    // Getters and setters
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public long getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(long evaluatedAt) { this.evaluatedAt = evaluatedAt; }

    public int getTotalQueries() { return totalQueries; }
    public void setTotalQueries(int totalQueries) { this.totalQueries = totalQueries; }

    public int getEvaluatedQueries() { return evaluatedQueries; }
    public void setEvaluatedQueries(int evaluatedQueries) { this.evaluatedQueries = evaluatedQueries; }

    public double getNdcg5() { return ndcg5; }
    public void setNdcg5(double ndcg5) { this.ndcg5 = ndcg5; }

    public double getNdcg10() { return ndcg10; }
    public void setNdcg10(double ndcg10) { this.ndcg10 = ndcg10; }

    public double getMrr10() { return mrr10; }
    public void setMrr10(double mrr10) { this.mrr10 = mrr10; }

    public Map<String, IntentMetrics> getByIntent() { return byIntent; }
    public void setByIntent(Map<String, IntentMetrics> byIntent) { this.byIntent = byIntent; }

    public Map<String, ModeMetrics> getByMode() { return byMode; }
    public void setByMode(Map<String, ModeMetrics> byMode) { this.byMode = byMode; }
}
