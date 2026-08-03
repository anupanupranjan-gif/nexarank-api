// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.nexarank.api.model.UserActionToken;
import com.nexarank.api.repository.UserActionTokenRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * NR-65: issues/validates/consumes single-use, expiring tokens for invite,
 * password-reset, and email-verification links — same hash-at-rest
 * principle as RefreshTokenService (the raw token is never persisted, only
 * its SHA-256 hash), applied to a different, shorter-lived use case.
 */
@Service
public class UserActionTokenService {

    private final UserActionTokenRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

    public UserActionTokenService(UserActionTokenRepository repository) {
        this.repository = repository;
    }

    public record IssuedToken(String rawToken, UserActionToken row) {}

    public IssuedToken issue(String userId, UserActionToken.Purpose purpose, long ttlMillis) {
        String raw = generateRawToken();
        UserActionToken row = new UserActionToken();
        row.setId(UUID.randomUUID().toString());
        row.setUserId(userId);
        row.setPurpose(purpose);
        row.setTokenHash(hash(raw));
        row.setExpiresAt(Instant.now().plus(ttlMillis, ChronoUnit.MILLIS));
        repository.save(row);
        return new IssuedToken(raw, row);
    }

    /** Returns the active (unused, unexpired) token row for the given raw value and purpose, if any. */
    public Optional<UserActionToken> findActive(String rawToken, UserActionToken.Purpose purpose) {
        return repository.findByTokenHash(hash(rawToken))
                .filter(UserActionToken::isActive)
                .filter(row -> row.getPurpose() == purpose);
    }

    public void consume(UserActionToken row) {
        row.setUsedAt(Instant.now());
        repository.save(row);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
