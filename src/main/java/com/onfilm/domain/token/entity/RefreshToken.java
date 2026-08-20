package com.onfilm.domain.token.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.regex.Pattern;

@Entity
@Table(
        name = "refresh_tokens",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_refresh_token_hash",
                columnNames = "token_hash"
        ),
        indexes = {
                @Index(name = "idx_refresh_token_user_id", columnList = "user_id"),
                @Index(name = "idx_refresh_token_expires_at", columnList = "expires_at"),
                @Index(name = "idx_refresh_token_revoked_at", columnList = "revoked_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {
    public static final int TOKEN_HASH_LENGTH = 43;
    private static final Pattern TOKEN_HASH_PATTERN = Pattern.compile(
            "[A-Za-z0-9_-]{" + TOKEN_HASH_LENGTH + "}"
    );

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(
            name = "token_hash",
            nullable = false,
            updatable = false,
            length = TOKEN_HASH_LENGTH
    )
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    private RefreshToken(
            Long userId,
            String tokenHash,
            Instant issuedAt,
            Instant expiresAt
    ) {
        this.userId = requirePositive(userId, "userId");
        this.tokenHash = requireTokenHash(tokenHash);
        this.createdAt = require(issuedAt, "issuedAt");
        this.expiresAt = requireExpirationAfter(issuedAt, expiresAt);
    }

    public static RefreshToken issue(
            Long userId,
            String tokenHash,
            Instant issuedAt,
            Instant expiresAt
    ) {
        return new RefreshToken(userId, tokenHash, issuedAt, expiresAt);
    }

    public void consume(Instant usedAt) {
        Instant requiredUsedAt = requireUseTime(usedAt, "usedAt");
        if (isRevoked()) {
            throw new IllegalStateException("refresh token already revoked");
        }
        if (isExpiredAt(requiredUsedAt)) {
            throw new IllegalStateException("refresh token expired");
        }

        this.lastUsedAt = requiredUsedAt;
        this.revokedAt = requiredUsedAt;
    }

    public void revoke(Instant revokedAt) {
        if (this.revokedAt != null) {
            return;
        }

        Instant requiredRevokedAt = requireUseTime(revokedAt, "revokedAt");
        this.lastUsedAt = requiredRevokedAt;
        this.revokedAt = requiredRevokedAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpiredAt(Instant instant) {
        Instant requiredInstant = require(instant, "instant");
        return !requiredInstant.isBefore(expiresAt);
    }

    public Long getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    private static Long requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    private static String requireTokenHash(String tokenHash) {
        if (tokenHash == null || tokenHash.isBlank()) {
            throw new IllegalArgumentException("tokenHash is required");
        }
        if (tokenHash.length() != TOKEN_HASH_LENGTH) {
            throw new IllegalArgumentException(
                    "tokenHash length must be " + TOKEN_HASH_LENGTH
            );
        }
        if (!TOKEN_HASH_PATTERN.matcher(tokenHash).matches()) {
            throw new IllegalArgumentException("tokenHash must be URL-safe Base64");
        }
        return tokenHash;
    }

    private static Instant requireExpirationAfter(
            Instant issuedAt,
            Instant expiresAt
    ) {
        Instant requiredExpiration = require(expiresAt, "expiresAt");
        if (!requiredExpiration.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
        return requiredExpiration;
    }

    private Instant requireUseTime(Instant value, String fieldName) {
        Instant requiredValue = require(value, fieldName);
        if (requiredValue.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    fieldName + " must not be before createdAt"
            );
        }
        return requiredValue;
    }

    private static <T> T require(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
