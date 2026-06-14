// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.security.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * API for the Pipeline Editor UI.
 *
 * GET  /api/v1/pipeline/stages                     — list all stages for current project
 * POST /api/v1/pipeline/stages/{stageName}/toggle  — enable/disable a stage
 */
@RestController
@RequestMapping("/api/v1/pipeline")
public class PipelineConfigController {

    private final JdbcTemplate jdbc;

    public PipelineConfigController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/stages")
    public ResponseEntity<List<Map<String, Object>>> getStages() {
        String tenantId  = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();

        List<Map<String, Object>> stages = jdbc.queryForList("""
        SELECT stage_name, stage_group, stage_order, enabled
        FROM pipeline_stage_config
        WHERE tenant_id = ? AND project_id = ?
        ORDER BY
          CASE stage_group
            WHEN 'PRE_QUERY' THEN 1
            WHEN 'RULE_APPLICATION' THEN 2
            WHEN 'POST_QUERY' THEN 3
          END,
          stage_order
        """, tenantId, projectId);

        // Map snake_case DB columns to camelCase for the UI
        List<Map<String, Object>> mapped = stages.stream().map(row ->
                Map.of(
                        "stageName",  row.get("stage_name"),
                        "stageGroup", row.get("stage_group"),
                        "stageOrder", row.get("stage_order"),
                        "enabled",    row.get("enabled")
                )
        ).toList();

        return ResponseEntity.ok(mapped);
    }

    @PostMapping("/stages/{stageName}/toggle")
    public ResponseEntity<Map<String, Object>> toggleStage(
            @PathVariable String stageName,
            @RequestBody Map<String, Object> body) {

        String tenantId  = TenantContext.getTenantId();
        String projectId = TenantContext.getProjectId();
        boolean enabled  = Boolean.TRUE.equals(body.get("enabled"));

        // RULE_APPLICATION cannot be disabled — it's the core stage
        if ("RULE_APPLICATION".equals(stageName)) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "RULE_APPLICATION stage cannot be disabled"));
        }

        int updated = jdbc.update("""
            UPDATE pipeline_stage_config
            SET enabled = ?, updated_at = NOW()
            WHERE tenant_id = ? AND project_id = ? AND stage_name = ?
            """, enabled, tenantId, projectId, stageName);

        if (updated == 0) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of(
            "stageName", stageName,
            "enabled", enabled
        ));
    }
}
