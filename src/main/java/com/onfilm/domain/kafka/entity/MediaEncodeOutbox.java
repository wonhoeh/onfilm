package com.onfilm.domain.kafka.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_encode_outbox",
        uniqueConstraints = @UniqueConstraint(name = "uk_media_encode_outbox_job", columnNames = "job_id"),
        indexes = {
                @Index(name = "idx_media_outbox_dispatch", columnList = "status,next_attempt_at"),
                @Index(name = "idx_media_outbox_status_published", columnList = "status,published_at"),
                @Index(name = "idx_media_outbox_status_created", columnList = "status,created_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaEncodeOutbox {
    public static final int MAX_ATTEMPTS = 8;
    public static final int ERROR_LENGTH = 1000;

    @Id
    @Column(nullable = false, updatable = false, length = MediaEncodeJob.ID_LENGTH)
    private String id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "job_id", nullable = false, updatable = false, length = MediaEncodeJob.ID_LENGTH)
    private String jobId;

    @Column(name = "schema_version", nullable = false, updatable = false)
    private int schemaVersion;

    @Lob
    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MediaEncodeOutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error", length = ERROR_LENGTH)
    private String lastError;

    private MediaEncodeOutbox(String id, String jobId, String payload, Instant createdAt) {
        this.id = canonicalUuid(id, "id");
        this.jobId = canonicalUuid(jobId, "jobId");
        this.payload = requireText(payload, "payload", 65535);
        this.schemaVersion = 1;
        this.status = MediaEncodeOutboxStatus.PENDING;
        this.createdAt = require(createdAt, "createdAt");
        this.nextAttemptAt = createdAt;
    }

    public static MediaEncodeOutbox pending(String id, String jobId, String payload, Instant createdAt) {
        return new MediaEncodeOutbox(id, jobId, payload, createdAt);
    }

    public void claim(Instant now, Duration lease) {
        if (!isClaimable(now)) throw new IllegalStateException("OUTBOX_NOT_CLAIMABLE");
        status = MediaEncodeOutboxStatus.PUBLISHING;
        attempts++;
        leaseUntil = now.plus(lease);
    }

    public boolean isClaimable(Instant now) {
        return (status == MediaEncodeOutboxStatus.PENDING && !nextAttemptAt.isAfter(now))
                || (status == MediaEncodeOutboxStatus.PUBLISHING && leaseUntil != null && !leaseUntil.isAfter(now));
    }

    public void published(Instant now) {
        if (status == MediaEncodeOutboxStatus.PUBLISHED) return;
        if (status != MediaEncodeOutboxStatus.PUBLISHING) throw new IllegalStateException("OUTBOX_NOT_PUBLISHING");
        status = MediaEncodeOutboxStatus.PUBLISHED;
        publishedAt = now;
        leaseUntil = null;
        lastError = null;
    }

    public void failed(String error, Instant now) {
        if (status != MediaEncodeOutboxStatus.PUBLISHING) throw new IllegalStateException("OUTBOX_NOT_PUBLISHING");
        lastError = normalizeError(error);
        leaseUntil = null;
        if (attempts >= MAX_ATTEMPTS) {
            status = MediaEncodeOutboxStatus.DEAD;
            nextAttemptAt = now;
            return;
        }
        status = MediaEncodeOutboxStatus.PENDING;
        long delaySeconds = Math.min(300, 1L << Math.min(attempts, 8));
        nextAttemptAt = now.plusSeconds(delaySeconds);
    }

    private static String normalizeError(String error) {
        String text = error == null || error.isBlank() ? "unknown publish failure" : error.trim();
        return text.length() <= ERROR_LENGTH ? text : text.substring(0, ERROR_LENGTH);
    }

    private static String canonicalUuid(String value, String field) {
        String text = requireText(value, field, MediaEncodeJob.ID_LENGTH);
        try {
            if (!UUID.fromString(text).toString().equals(text)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(field + " must be a canonical UUID");
        }
        return text;
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        String text = value.trim();
        if (text.length() > max) throw new IllegalArgumentException(field + " is too long");
        return text;
    }

    private static <T> T require(T value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }
}
