// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.nexarank.api.model.Project;
import com.nexarank.api.model.Tenant;
import com.nexarank.api.model.User;
import com.nexarank.api.repository.ProjectRepository;
import com.nexarank.api.repository.TenantRepository;
import com.nexarank.api.repository.UserRepository;
import com.nexarank.api.service.AnalyticsService.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * NR-36: last of the five Advanced Reporting sub-items — a monthly rollup
 * email covering the reporting surfaces built earlier in this same ticket
 * (search health, facet usage, rule performance) that the existing weekly
 * digest (EmailReportService) doesn't cover — that one is focused on rule/
 * zero-result/watched-query/signal alerts on a weekly cadence, this is a
 * calendar-month retrospective built on the exact same AnalyticsService
 * aggregation the dashboard and PDF export use, so the numbers always match.
 *
 * Loops every enabled tenant's enabled projects (a tenant with more than one
 * enabled project gets one email per project, same project-scoping as the
 * PDF export) and emails each tenant's users with an email on file — same
 * recipient-sourcing convention as EmailReportService (NR-67).
 *
 * Schedule: 8am on the 1st of the month, covering the previous calendar
 * month (configurable via nexarank.monthly-report.cron).
 */
@Service
public class MonthlySummaryEmailService {

    private static final Logger log = LoggerFactory.getLogger(MonthlySummaryEmailService.class);
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US);

    private final JavaMailSender mailSender;
    private final AnalyticsService analyticsService;
    private final TenantRepository tenantRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    @Value("${nexarank.report.from:modernreliability@gmail.com}")
    private String fromAddress;

    public MonthlySummaryEmailService(JavaMailSender mailSender,
                                       AnalyticsService analyticsService,
                                       TenantRepository tenantRepository,
                                       ProjectRepository projectRepository,
                                       UserRepository userRepository) {
        this.mailSender = mailSender;
        this.analyticsService = analyticsService;
        this.tenantRepository = tenantRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    @Scheduled(cron = "${nexarank.monthly-report.cron:0 0 8 1 * *}")
    public void sendMonthlySummaries() {
        LocalDate previousMonth = LocalDate.now(ZoneOffset.UTC).minusMonths(1);
        log.info("Sending NexaRank monthly summary for {}", MONTH_FMT.format(previousMonth));
        for (Tenant tenant : tenantRepository.findByEnabled(true)) {
            List<User> recipients = userRepository.findByTenantIdAndEmailIsNotNull(tenant.getId());
            if (recipients.isEmpty()) continue;
            for (Project project : projectRepository.findByTenantIdAndEnabled(tenant.getId(), true)) {
                for (User recipient : recipients) {
                    sendSummaryToRecipient(tenant.getId(), project.getId(), project.getName(),
                            previousMonth.getYear(), previousMonth.getMonthValue(), recipient.getEmail());
                }
            }
        }
    }

    /** Manual trigger — called from ReportController for test sends. Covers the previous calendar month. */
    public void sendTestMonthlySummary(String tenantId, String projectId, String recipient) {
        Project project = projectRepository.findByTenantIdAndId(tenantId, projectId).orElse(null);
        String projectName = project != null ? project.getName() : projectId;
        LocalDate previousMonth = LocalDate.now(ZoneOffset.UTC).minusMonths(1);
        log.info("Sending test monthly summary to {}", recipient);
        sendSummaryToRecipient(tenantId, projectId, projectName,
                previousMonth.getYear(), previousMonth.getMonthValue(), recipient);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void sendSummaryToRecipient(String tenantId, String projectId, String projectName,
                                         int year, int month, String recipient) {
        try {
            LocalDate monthStart = LocalDate.of(year, month, 1);
            String subject = String.format("NexaRank Monthly Summary — %s — %s",
                    projectName, MONTH_FMT.format(monthStart));
            String html = buildEmailHtml(tenantId, projectId, projectName, year, month);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(message);
            log.info("Monthly summary ({}/{}) sent to {}", tenantId, projectName, recipient);
        } catch (Exception e) {
            log.error("Failed to send monthly summary ({}/{}) to {}: {}", tenantId, projectName, recipient, e.getMessage(), e);
            throw new RuntimeException("Email send failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private String buildEmailHtml(String tenantId, String projectId, String projectName, int year, int month) {
        Window window = analyticsService.monthWindow(year, month);
        int days = (int) java.time.Duration.between(window.since(), window.until()).toDays();

        Map<String, Object> overview = analyticsService.buildOverview(tenantId, projectId, window, days);
        Map<String, Object> searchHealth = analyticsService.buildSearchHealth(tenantId, projectId, window, days);
        Map<String, Object> facetUsage = analyticsService.buildFacetUsage(tenantId, projectId, window, days);
        Map<String, Object> rulesPerformance = analyticsService.buildRulesPerformance(tenantId, projectId, window, days);

        Map<String, Object> latency = (Map<String, Object>) searchHealth.get("latency");
        List<Map<String, Object>> unusedFacets = (List<Map<String, Object>>) facetUsage.get("unusedFacets");
        List<Map<String, Object>> rules = (List<Map<String, Object>>) rulesPerformance.get("rules");
        List<Map<String, Object>> topRules = rules.stream()
                .sorted((a, b) -> Long.compare(((Number) b.get("firedCount")).longValue(), ((Number) a.get("firedCount")).longValue()))
                .limit(5)
                .toList();

        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="UTF-8">
            <style>
              body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; background: #f8fafc; margin: 0; padding: 20px; }
              .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; overflow: hidden; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
              .header { background: linear-gradient(135deg, #0077ff, #4f46e5); padding: 32px; color: white; }
              .header h1 { margin: 0 0 8px; font-size: 24px; }
              .header p { margin: 0; opacity: 0.8; font-size: 14px; }
              .section { padding: 24px 32px; border-bottom: 1px solid #f1f5f9; }
              .section h2 { margin: 0 0 16px; font-size: 16px; color: #1e293b; }
              .kpi-row { display: flex; gap: 16px; flex-wrap: wrap; }
              .kpi { flex: 1; min-width: 100px; background: #f8fafc; border-radius: 8px; padding: 16px; text-align: center; }
              .kpi-value { font-size: 24px; font-weight: 800; color: #1e293b; }
              .kpi-label { font-size: 12px; color: #64748b; margin-top: 4px; }
              .alert { padding: 12px 16px; border-radius: 8px; margin-bottom: 8px; font-size: 13px; }
              .alert-warn { background: #fef2f2; border: 1px solid #fca5a5; color: #dc2626; }
              .alert-ok { background: #f0fdf4; border: 1px solid #86efac; color: #16a34a; }
              table { width: 100%%; border-collapse: collapse; font-size: 13px; }
              th { text-align: left; padding: 6px 8px; color: #64748b; font-size: 11px; text-transform: uppercase; border-bottom: 1px solid #e2e8f0; }
              td { padding: 6px 8px; border-bottom: 1px solid #f1f5f9; color: #334155; }
              .footer { padding: 24px 32px; background: #f8fafc; font-size: 12px; color: #94a3b8; text-align: center; }
            </style>
            </head>
            <body>
            <div class="container">

              <div class="header">
                <h1>NexaRank Monthly Summary</h1>
                <p>%s | %s / %s</p>
              </div>

              <div class="section">
                <h2>📊 Overview</h2>
                <div class="kpi-row">
                  <div class="kpi"><div class="kpi-value">%s</div><div class="kpi-label">Searches</div></div>
                  <div class="kpi"><div class="kpi-value">%s</div><div class="kpi-label">Clicks</div></div>
                  <div class="kpi"><div class="kpi-value">%s</div><div class="kpi-label">Avg CTR</div></div>
                  <div class="kpi"><div class="kpi-value">%s</div><div class="kpi-label">Zero-Result Rate</div></div>
                </div>
              </div>

              <div class="section">
                <h2>⚡ Search Health</h2>
                <p style="font-size:13px;color:#475569;margin:0 0 8px">
                  Latency p50 / p95 / p99: <strong>%s / %s / %s</strong> (%s sampled searches)
                </p>
              </div>

              <div class="section">
                <h2>🔎 Facet Usage</h2>
                %s
              </div>

              <div class="section">
                <h2>📈 Top Rules by Volume</h2>
                %s
              </div>

              <div class="footer">
                Generated by NexaRank · <a href="http://localhost/nexarank-ui" style="color:#4f46e5">Open Dashboard</a>
              </div>
            </div>
            </body>
            </html>
            """,
            MONTH_FMT.format(LocalDate.of(year, month, 1)), tenantId, projectName,
            overview.get("totalSearches"), overview.get("totalClicks"),
            formatPct(overview.get("avgCtr")), formatPct(overview.get("zeroResultRate")),
            formatMs(latency.get("p50")), formatMs(latency.get("p95")), formatMs(latency.get("p99")),
            latency.get("sampleSize"),
            buildUnusedFacetsHtml(unusedFacets),
            buildTopRulesHtml(topRules)
        );
    }

    private String formatPct(Object o) {
        if (o == null) return "-";
        return String.format("%.1f%%", ((Number) o).doubleValue() * 100.0);
    }

    private String formatMs(Object o) {
        return o == null ? "-" : o + "ms";
    }

    private String buildUnusedFacetsHtml(List<Map<String, Object>> unusedFacets) {
        if (unusedFacets.isEmpty()) {
            return "<div class=\"alert alert-ok\">✓ Every configured facet saw at least one selection this month</div>";
        }
        StringBuilder sb = new StringBuilder("<div class=\"alert alert-warn\">Unused this month: ");
        sb.append(unusedFacets.stream().map(f -> String.valueOf(f.get("displayLabel")))
                .reduce((a, b) -> a + ", " + b).orElse(""));
        sb.append("</div>");
        return sb.toString();
    }

    private String buildTopRulesHtml(List<Map<String, Object>> topRules) {
        if (topRules.isEmpty()) {
            return "<div class=\"alert alert-ok\">No rules configured yet.</div>";
        }
        StringBuilder sb = new StringBuilder("<table><tr><th>Type</th><th>Query</th><th>Fired</th><th>CTR</th></tr>");
        for (Map<String, Object> r : topRules) {
            sb.append(String.format("<tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>",
                    r.get("type"), r.get("query"), r.get("firedCount"), formatPct(r.get("ctr"))));
        }
        sb.append("</table>");
        return sb.toString();
    }
}
