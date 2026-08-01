// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.controller;

import com.nexarank.api.service.EmailReportService;
import com.nexarank.api.service.MonthlySummaryEmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Manual report triggers.
 *
 * POST /api/v1/reports/send-test          — send test weekly digest to specified recipient
 * POST /api/v1/reports/send-now           — trigger weekly digest immediately
 * POST /api/v1/reports/send-monthly-test  — send test monthly summary to specified recipient
 * POST /api/v1/reports/send-monthly-now   — trigger monthly summary immediately for all tenants
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final EmailReportService emailReportService;
    private final MonthlySummaryEmailService monthlySummaryEmailService;

    public ReportController(EmailReportService emailReportService,
                             MonthlySummaryEmailService monthlySummaryEmailService) {
        this.emailReportService = emailReportService;
        this.monthlySummaryEmailService = monthlySummaryEmailService;
    }

    @PostMapping("/send-test")
    public ResponseEntity<Map<String, Object>> sendTest(
            @RequestBody Map<String, String> body) {
        String recipient = body.getOrDefault("recipient", "modernreliability@gmail.com");
        String tenantId  = body.getOrDefault("tenantId", "default");
        String projectId = body.getOrDefault("projectId", "main");
        try {
            emailReportService.sendTestReport(tenantId, projectId, recipient);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Test report sent to " + recipient
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/send-now")
    public ResponseEntity<Map<String, Object>> sendNow() {
        try {
            emailReportService.sendWeeklyDigest();
            return ResponseEntity.ok(Map.of("success", true, "message", "Weekly digest sent"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false, "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/send-monthly-test")
    public ResponseEntity<Map<String, Object>> sendMonthlyTest(
            @RequestBody Map<String, String> body) {
        String recipient = body.getOrDefault("recipient", "modernreliability@gmail.com");
        String tenantId  = body.getOrDefault("tenantId", "default");
        String projectId = body.getOrDefault("projectId", "main");
        try {
            monthlySummaryEmailService.sendTestMonthlySummary(tenantId, projectId, recipient);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Test monthly summary sent to " + recipient
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/send-monthly-now")
    public ResponseEntity<Map<String, Object>> sendMonthlyNow() {
        try {
            monthlySummaryEmailService.sendMonthlySummaries();
            return ResponseEntity.ok(Map.of("success", true, "message", "Monthly summaries sent"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false, "message", e.getMessage()
            ));
        }
    }
}
