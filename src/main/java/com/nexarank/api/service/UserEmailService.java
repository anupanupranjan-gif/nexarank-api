// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.nexarank.api.model.User;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/** NR-65: invite/password-reset/email-verification emails. Same JavaMailSender pattern as RuleNotificationService. */
@Service
public class UserEmailService {

    private static final Logger log = LoggerFactory.getLogger(UserEmailService.class);
    private static final String APP_URL = "http://localhost/nexarank-ui";

    private final JavaMailSender mailSender;

    @Value("${nexarank.report.from:modernreliability@gmail.com}")
    private String fromAddress;

    public UserEmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendInvite(User user, String rawToken) {
        String link = APP_URL + "/accept-invite?token=" + rawToken;
        send(user.getEmail(), "You've been invited to NexaRank",
                "You've been invited to join NexaRank as " + user.getRole().name() + ".\n\n"
                        + "Set your password here to get started: " + link + "\n\n"
                        + "This link expires in 7 days.");
    }

    public void sendPasswordReset(User user, String rawToken) {
        String link = APP_URL + "/reset-password?token=" + rawToken;
        send(user.getEmail(), "NexaRank password reset",
                "A password reset was requested for your NexaRank account (" + user.getUsername() + ").\n\n"
                        + "Reset your password here: " + link + "\n\n"
                        + "This link expires in 1 hour. If you didn't request this, you can ignore this email.");
    }

    public void sendVerification(User user, String rawToken) {
        String link = APP_URL + "/verify-email?token=" + rawToken;
        send(user.getEmail(), "Verify your NexaRank email",
                "Please verify your email address to activate your NexaRank account (" + user.getUsername() + ").\n\n"
                        + "Verify here: " + link + "\n\n"
                        + "This link expires in 48 hours.");
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
        } catch (Exception e) {
            log.warn("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
