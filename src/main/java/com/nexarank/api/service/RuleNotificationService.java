// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.nexarank.api.model.MerchRule;
import com.nexarank.api.model.User;
import com.nexarank.api.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * NR-68: email notifications for the rule approval workflow.
 * Recipients always come from the email addresses on user records (NR-67) —
 * never hardcoded.
 */
@Service
public class RuleNotificationService {

    private static final Logger log = LoggerFactory.getLogger(RuleNotificationService.class);
    private static final String APP_URL = "http://localhost/nexarank-ui";

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;

    @Value("${nexarank.report.from:modernreliability@gmail.com}")
    private String fromAddress;

    public RuleNotificationService(JavaMailSender mailSender, UserRepository userRepository) {
        this.mailSender = mailSender;
        this.userRepository = userRepository;
    }

    /** DRAFT -> PENDING_REVIEW: notify every APPROVER and ADMIN in the tenant. */
    public void notifySubmitted(MerchRule rule) {
        try {
            List<User> recipients = new ArrayList<>();
            recipients.addAll(userRepository.findByTenantIdAndRoleAndEmailIsNotNull(rule.getTenantId(), User.Role.APPROVER));
            recipients.addAll(userRepository.findByTenantIdAndRoleAndEmailIsNotNull(rule.getTenantId(), User.Role.ADMIN));

            String subject = "NexaRank: rule pending review — " + rule.getQuery();
            String body = "A rule was submitted for review.\n\n"
                    + ruleSummary(rule)
                    + "\nReview it here: " + APP_URL;
            recipients.forEach(u -> send(u.getEmail(), subject, body));
        } catch (Exception e) {
            log.warn("Failed to notify approvers for rule {}: {}", rule.getId(), e.getMessage());
        }
    }

    /**
     * PENDING_REVIEW -> APPROVED (and possibly straight on to LIVE in the same
     * action, via tenant auto-publish or the approver's own publish-now choice).
     * One email either way — if it went live separately later, promoteToLive
     * sends its own follow-up email instead of this one repeating.
     */
    public void notifyApproved(MerchRule rule, String comment, boolean wentLive) {
        try {
            userRepository.findByTenantIdAndUsername(rule.getTenantId(), rule.getSubmittedBy())
                    .filter(u -> u.getEmail() != null && !u.getEmail().isBlank())
                    .ifPresent(creator -> {
                        String subject = "NexaRank: rule approved — " + rule.getQuery();
                        String status = wentLive
                                ? "Your rule was approved and is now LIVE."
                                : "Your rule was approved and is pending publish by an admin.";
                        String body = status + "\n\n" + ruleSummary(rule)
                                + (comment != null && !comment.isBlank() ? "\nApprover comment: " + comment + "\n" : "")
                                + "\nOpen NexaRank: " + APP_URL;
                        send(creator.getEmail(), subject, body);
                    });
        } catch (Exception e) {
            log.warn("Failed to notify creator of approval for rule {}: {}", rule.getId(), e.getMessage());
        }
    }

    /** APPROVED -> LIVE, as its own later, separate action (manual admin publish). */
    public void notifyPublished(MerchRule rule) {
        try {
            userRepository.findByTenantIdAndUsername(rule.getTenantId(), rule.getSubmittedBy())
                    .filter(u -> u.getEmail() != null && !u.getEmail().isBlank())
                    .ifPresent(creator -> {
                        String subject = "NexaRank: rule now live — " + rule.getQuery();
                        String body = "Your rule is now LIVE.\n\n" + ruleSummary(rule)
                                + "\nOpen NexaRank: " + APP_URL;
                        send(creator.getEmail(), subject, body);
                    });
        } catch (Exception e) {
            log.warn("Failed to notify creator of publish for rule {}: {}", rule.getId(), e.getMessage());
        }
    }

    /** PENDING_REVIEW -> DRAFT (rejected): creator sees the reason, can revise and resubmit. */
    public void notifyRejected(MerchRule rule, String comment) {
        try {
            userRepository.findByTenantIdAndUsername(rule.getTenantId(), rule.getSubmittedBy())
                    .filter(u -> u.getEmail() != null && !u.getEmail().isBlank())
                    .ifPresent(creator -> {
                        String subject = "NexaRank: rule rejected — " + rule.getQuery();
                        String body = "Your rule was rejected and returned to draft.\n\n" + ruleSummary(rule)
                                + (comment != null && !comment.isBlank() ? "\nReason: " + comment + "\n" : "")
                                + "\nRevise and resubmit here: " + APP_URL;
                        send(creator.getEmail(), subject, body);
                    });
        } catch (Exception e) {
            log.warn("Failed to notify creator of rejection for rule {}: {}", rule.getId(), e.getMessage());
        }
    }

    private String ruleSummary(MerchRule rule) {
        StringBuilder sb = new StringBuilder();
        sb.append("Type: ").append(rule.getType()).append("\n");
        sb.append("Query trigger: ").append(rule.getQuery()).append("\n");
        if (rule.getBoostField() != null) {
            sb.append("Field/Value: ").append(rule.getBoostField()).append(" = ").append(rule.getBoostValue()).append("\n");
        }
        if (rule.getBoostFactor() != null) {
            sb.append("Boost factor: ").append(rule.getBoostFactor()).append("\n");
        }
        sb.append("Submitted by: ").append(rule.getSubmittedBy()).append("\n");
        return sb.toString();
    }

    private void send(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(message);
            log.info("Rule notification sent to {}: {}", to, subject);
        } catch (Exception e) {
            log.warn("Failed to send rule notification email to {}: {}", to, e.getMessage());
        }
    }
}
