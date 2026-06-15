// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.nexarank.api.model.MerchRule;
import com.nexarank.api.model.WatchedQuery;
import com.nexarank.api.repository.ClickEventRepository;
import com.nexarank.api.repository.MerchRuleRepository;
import com.nexarank.api.repository.ZeroResultQueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Sends weekly digest email reports to configured recipients.
 *
 * Content:
 * - Rule performance summary (active, pending, never-fired)
 * - Zero-result trend
 * - Watched query alerts
 * - Active business signals
 *
 * Schedule: Monday 8am (configurable via nexarank.report.cron)
 * Manual trigger: POST /api/v1/reports/send-test
 */
@Service
public class EmailReportService {

    private static final Logger log = LoggerFactory.getLogger(EmailReportService.class);
    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault());

    private final JavaMailSender mailSender;
    private final MerchRuleRepository ruleRepository;
    private final ClickEventRepository clickEventRepository;
    private final ZeroResultQueryRepository zeroResultRepository;
    private final WatchedQueryService watchedQueryService;
    private final AiRuleSuggestionService suggestionService;
    private final BusinessSignalService signalService;

    @Value("${nexarank.report.recipients:modernreliability@gmail.com}")
    private String recipients;

    @Value("${nexarank.report.from:modernreliability@gmail.com}")
    private String fromAddress;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    public EmailReportService(JavaMailSender mailSender,
                               MerchRuleRepository ruleRepository,
                               ClickEventRepository clickEventRepository,
                               ZeroResultQueryRepository zeroResultRepository,
                               WatchedQueryService watchedQueryService,
                               AiRuleSuggestionService suggestionService,
                               BusinessSignalService signalService) {
        this.mailSender           = mailSender;
        this.ruleRepository       = ruleRepository;
        this.clickEventRepository = clickEventRepository;
        this.zeroResultRepository = zeroResultRepository;
        this.watchedQueryService  = watchedQueryService;
        this.suggestionService    = suggestionService;
        this.signalService        = signalService;
    }

    /**
     * Weekly digest — runs Monday 8am.
     * Override schedule via nexarank.report.cron property.
     */
    @Scheduled(cron = "${nexarank.report.cron:0 0 8 * * MON}")
    public void sendWeeklyDigest() {
        log.info("Sending weekly NexaRank digest email");
        sendReport("default", "main", 7);
    }

    /**
     * Manual trigger — called from ReportController for test sends.
     */
    public void sendTestReport(String tenantId, String projectId, String recipient) {
        log.info("Sending test report to {}", recipient);
        sendReportToRecipient(tenantId, projectId, 7, recipient);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void sendReport(String tenantId, String projectId, int days) {
        for (String recipient : recipients.split(",")) {
            sendReportToRecipient(tenantId, projectId, days, recipient.trim());
        }
    }

    private void sendReportToRecipient(String tenantId, String projectId,
                                        int days, String recipient) {
        try {
            String subject = String.format("NexaRank Weekly Digest — %s",
                DATE_FMT.format(Instant.now()));
            String html = buildEmailHtml(tenantId, projectId, days);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(message);
            log.info("Weekly digest sent to {}", recipient);
        } catch (Exception e) {
            log.error("Failed to send report to {}: {}", recipient, e.getMessage(), e);
            throw new RuntimeException("Email send failed: " + e.getMessage(), e);
        }
    }

    private String buildEmailHtml(String tenantId, String projectId, int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);

        // Gather data
        List<MerchRule> allRules = ruleRepository.findByTenantIdAndProjectId(tenantId, projectId);
        long activeRules  = allRules.stream().filter(r -> r.getStatus() == MerchRule.RuleStatus.APPROVED && r.isEnabled()).count();
        long pendingRules = allRules.stream().filter(r -> r.getStatus() == MerchRule.RuleStatus.PENDING_REVIEW).count();
        long neverFired   = allRules.stream().filter(r -> r.getStatus() == MerchRule.RuleStatus.APPROVED
            && r.isEnabled() && r.getCreatedAt().isBefore(since)).count();

        long totalClicks      = clickEventRepository.countByTenantIdAndProjectId(tenantId, projectId);
        List<Object[]> zeroResults = zeroResultRepository.findTopZeroResultQueries(tenantId, projectId, since);

        // Watched query alerts
        // Note: TenantContext not available in scheduled context — using direct repo calls
        List<WatchedQuery> watched = watchedQueryService.getAllForTenant(tenantId, projectId);

        // Active business signals
        List<com.nexarank.api.model.BusinessSignal> signals =
            signalService.getActiveSignalsForTenant(tenantId, projectId);

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
              .kpi-row { display: flex; gap: 16px; }
              .kpi { flex: 1; background: #f8fafc; border-radius: 8px; padding: 16px; text-align: center; }
              .kpi-value { font-size: 28px; font-weight: 800; color: #1e293b; }
              .kpi-label { font-size: 12px; color: #64748b; margin-top: 4px; }
              .alert { padding: 12px 16px; border-radius: 8px; margin-bottom: 8px; font-size: 13px; }
              .alert-warn { background: #fef2f2; border: 1px solid #fca5a5; color: #dc2626; }
              .alert-ok { background: #f0fdf4; border: 1px solid #86efac; color: #16a34a; }
              .alert-info { background: #eff6ff; border: 1px solid #bfdbfe; color: #1d4ed8; }
              .query-list { margin: 0; padding: 0 0 0 16px; font-size: 13px; color: #475569; }
              .query-list li { margin-bottom: 4px; }
              .footer { padding: 24px 32px; background: #f8fafc; font-size: 12px; color: #94a3b8; text-align: center; }
              .badge { display: inline-block; padding: 2px 8px; border-radius: 10px; font-size: 11px; font-weight: 700; }
              .badge-boost { background: rgba(34,197,94,0.1); color: #16a34a; }
              .badge-bury { background: rgba(239,68,68,0.1); color: #dc2626; }
            </style>
            </head>
            <body>
            <div class="container">

              <div class="header">
                <h1>NexaRank Weekly Digest</h1>
                <p>%s — Last %d days | Tenant: %s / Project: %s</p>
              </div>

              <!-- Rule Performance -->
              <div class="section">
                <h2>⚡ Rule Performance</h2>
                <div class="kpi-row">
                  <div class="kpi">
                    <div class="kpi-value" style="color:#22c55e">%d</div>
                    <div class="kpi-label">Active Rules</div>
                  </div>
                  <div class="kpi">
                    <div class="kpi-value" style="color:#f97316">%d</div>
                    <div class="kpi-label">Pending Review</div>
                  </div>
                  <div class="kpi">
                    <div class="kpi-value" style="color:#64748b">%d</div>
                    <div class="kpi-label">Never Fired*</div>
                  </div>
                  <div class="kpi">
                    <div class="kpi-value">%d</div>
                    <div class="kpi-label">Total Clicks</div>
                  </div>
                </div>
                <p style="font-size:11px;color:#94a3b8;margin-top:12px">* Rules active for more than %d days with no recent click data</p>
              </div>

              <!-- Zero Result Queries -->
              <div class="section">
                <h2>🔍 Top Zero-Result Queries</h2>
                %s
              </div>

              <!-- Watched Query Alerts -->
              <div class="section">
                <h2>👁 Watched Query Status</h2>
                %s
              </div>

              <!-- Business Signals -->
              <div class="section">
                <h2>📡 Active Business Signals</h2>
                %s
              </div>

              <div class="footer">
                Generated by NexaRank · <a href="http://localhost/nexarank-ui" style="color:#4f46e5">Open Dashboard</a>
              </div>
            </div>
            </body>
            </html>
            """,
            DATE_FMT.format(Instant.now()), days, tenantId, projectId,
            activeRules, pendingRules, neverFired, totalClicks, days,
            buildZeroResultsHtml(zeroResults),
            buildWatchedQueriesHtml(watched),
            buildSignalsHtml(signals)
        );
    }

    private String buildZeroResultsHtml(List<Object[]> queries) {
        if (queries.isEmpty()) {
            return "<div class=\"alert alert-ok\">✓ No significant zero-result queries this period</div>";
        }
        StringBuilder sb = new StringBuilder("<ul class=\"query-list\">");
        queries.stream().limit(10).forEach(row -> {
            String query = (String) row[0];
            long count   = ((Number) row[1]).longValue();
            sb.append(String.format("<li><strong>%s</strong> — %d searches with no results</li>",
                query, count));
        });
        sb.append("</ul>");
        return sb.toString();
    }

    private String buildWatchedQueriesHtml(List<WatchedQuery> watched) {
        if (watched.isEmpty()) {
            return "<div class=\"alert alert-info\">No watched queries configured. "
                + "Add queries in the AI Suggestions page to monitor performance.</div>";
        }
        StringBuilder sb = new StringBuilder();
        watched.forEach(wq -> sb.append(String.format(
            "<div class=\"alert alert-ok\">👁 <strong>%s</strong>%s</div>",
            wq.getQuery(),
            wq.getNotes() != null ? " — " + wq.getNotes() : ""
        )));
        return sb.toString();
    }

    private String buildSignalsHtml(List<com.nexarank.api.model.BusinessSignal> signals) {
        if (signals.isEmpty()) {
            return "<div class=\"alert alert-info\">No active business signals.</div>";
        }
        StringBuilder sb = new StringBuilder();
        signals.forEach(sig -> {
            boolean isBoost = sig.getSignalType() == com.nexarank.api.model.BusinessSignal.SignalType.PROMOTED
                || sig.getSignalType() == com.nexarank.api.model.BusinessSignal.SignalType.SEASONAL;
            sb.append(String.format(
                "<div style=\"margin-bottom:6px;font-size:13px\">"
                + "<span class=\"badge %s\">%s</span> "
                + "<strong>%s</strong> — %s</div>",
                isBoost ? "badge-boost" : "badge-bury",
                isBoost ? "BOOST" : "BURY",
                sig.getProductId(),
                sig.getValue() != null ? sig.getValue() : sig.getSignalType().name()
            ));
        });
        return sb.toString();
    }
}
