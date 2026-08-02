// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.service;

import com.nexarank.api.model.RefreshToken;
import com.nexarank.api.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * NR-120: issues/rotates/revokes refresh tokens backing short-lived access
 * tokens. The raw token is a 256-bit SecureRandom value, never persisted —
 * only its SHA-256 hash is stored, so a DB read alone can't yield a usable
 * credential (same principle as password hashing, without the deliberate
 * slowness since this is already high-entropy, not a human-chosen secret).
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${nexarank.jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    public record IssuedToken(String rawToken, RefreshToken row) {}

    public IssuedToken issue(String userId, String tenantId, String deviceInfo, String ipAddress) {
        String raw = generateRawToken();
        RefreshToken row = new RefreshToken();
        row.setId(UUID.randomUUID().toString());
        row.setUserId(userId);
        row.setTenantId(tenantId);
        row.setTokenHash(hash(raw));
        row.setDeviceInfo(truncate(deviceInfo, 255));
        row.setIpAddress(ipAddress);
        Instant now = Instant.now();
        row.setIssuedAt(now);
        row.setExpiresAt(now.plus(refreshExpirationMs, ChronoUnit.MILLIS));
        RefreshToken saved = refreshTokenRepository.save(row);
        return new IssuedToken(raw, saved);
    }

    /**
     * Validates the presented raw token and rotates it: a new raw value is
     * generated and the SAME row's hash/last-used are updated in place
     * (issued_at stays the original issuance time, so "list sessions" shows
     * how long the session has really existed, not reset on every refresh).
     * Returns empty if the token is unknown, revoked, or expired.
     */
    public Optional<IssuedToken> rotate(String rawToken, String deviceInfo, String ipAddress) {
        return refreshTokenRepository.findByTokenHash(hash(rawToken))
                .filter(RefreshToken::isActive)
                .map(row -> {
                    String newRaw = generateRawToken();
                    row.setTokenHash(hash(newRaw));
                    row.setLastUsedAt(Instant.now());
                    if (deviceInfo != null) row.setDeviceInfo(truncate(deviceInfo, 255));
                    if (ipAddress != null) row.setIpAddress(ipAddress);
                    RefreshToken saved = refreshTokenRepository.save(row);
                    return new IssuedToken(newRaw, saved);
                });
    }

    public Optional<RefreshToken> findActiveByRawToken(String rawToken) {
        return refreshTokenRepository.findByTokenHash(hash(rawToken)).filter(RefreshToken::isActive);
    }

    /** Self-service revoke — only revokes if the token actually belongs to the requesting user. */
    public boolean revokeOwn(String tokenId, String requestingUserId) {
        return refreshTokenRepository.findById(tokenId)
                .filter(row -> row.getUserId().equals(requestingUserId))
                .map(row -> { row.setRevokedAt(Instant.now()); refreshTokenRepository.save(row); return true; })
                .orElse(false);
    }

    public void revokeAllForUser(String userId) {
        refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId)
                .forEach(row -> { row.setRevokedAt(Instant.now()); refreshTokenRepository.save(row); });
    }

    /** Admin revoke — only revokes if the token belongs to a user in the admin's own tenant. */
    public boolean revokeAsAdmin(String tokenId, String adminTenantId) {
        return refreshTokenRepository.findById(tokenId)
                .filter(row -> row.getTenantId().equals(adminTenantId))
                .map(row -> { row.setRevokedAt(Instant.now()); refreshTokenRepository.save(row); return true; })
                .orElse(false);
    }

    public List<RefreshToken> listActiveForUser(String userId) {
        return refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId).stream()
                .filter(RefreshToken::isActive).toList();
    }

    public List<RefreshToken> listActiveForTenant(String tenantId) {
        return refreshTokenRepository.findByTenantIdAndRevokedAtIsNull(tenantId).stream()
                .filter(RefreshToken::isActive).toList();
    }

    public long getRefreshExpirationMs() {
        return refreshExpirationMs;
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

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
