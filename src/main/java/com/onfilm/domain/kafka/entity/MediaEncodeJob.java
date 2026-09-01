package com.onfilm.domain.kafka.entity;

import com.onfilm.domain.common.error.exception.InvalidMediaJobStatusTransitionException;
import com.onfilm.domain.kafka.message.EncodeJobPreset;
import com.onfilm.domain.kafka.message.EncodeJobType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_encode_jobs",
        uniqueConstraints = @UniqueConstraint(name = "uk_media_encode_job_request", columnNames = "request_id"),
        indexes = {
                @Index(name = "idx_media_encode_job_user_status", columnList = "requested_by_user_id,status"),
                @Index(name = "idx_media_encode_job_status_requested", columnList = "status,requested_at"),
                @Index(name = "idx_media_encode_job_status_completed", columnList = "status,completed_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaEncodeJob {
    public static final int ID_LENGTH = 36;
    public static final int BUCKET_LENGTH = 63;
    public static final int KEY_LENGTH = 512;
    public static final int CONTENT_TYPE_LENGTH = 128;
    public static final int FAILURE_CODE_LENGTH = 64;
    public static final int FAILURE_REASON_LENGTH = 1000;

    @Id
    @Column(nullable = false, updatable = false, length = ID_LENGTH)
    private String id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "request_id", nullable = false, updatable = false, length = ID_LENGTH)
    private String requestId;

    @Column(name = "movie_id", nullable = false, updatable = false)
    private Long movieId;

    @Column(name = "requested_by_user_id", nullable = false, updatable = false)
    private Long requestedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, updatable = false, length = 32)
    private EncodeJobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 64)
    private EncodeJobPreset preset;

    @Column(name = "source_bucket", nullable = false, updatable = false, length = BUCKET_LENGTH)
    private String sourceBucket;

    @Column(name = "source_key", nullable = false, updatable = false, length = KEY_LENGTH)
    private String sourceKey;

    @Column(name = "target_bucket", nullable = false, updatable = false, length = BUCKET_LENGTH)
    private String targetBucket;

    @Column(name = "target_key", nullable = false, updatable = false, length = KEY_LENGTH)
    private String targetKey;

    @Column(name = "source_content_type", nullable = false, updatable = false, length = CONTENT_TYPE_LENGTH)
    private String sourceContentType;

    @Column(name = "target_content_type", nullable = false, updatable = false, length = CONTENT_TYPE_LENGTH)
    private String targetContentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MediaEncodeJobStatus status;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failure_code", length = FAILURE_CODE_LENGTH)
    private String failureCode;

    @Column(name = "failure_reason", length = FAILURE_REASON_LENGTH)
    private String failureReason;

    private MediaEncodeJob(String id, String requestId, Long movieId, Long requestedByUserId,
                           EncodeJobType jobType, EncodeJobPreset preset,
                           String sourceBucket, String sourceKey, String targetBucket, String targetKey,
                           String sourceContentType, String targetContentType, Instant requestedAt) {
        this.id = requireUuid(id, "id");
        this.requestId = requireUuid(requestId, "requestId");
        this.movieId = requirePositive(movieId, "movieId");
        this.requestedByUserId = requirePositive(requestedByUserId, "requestedByUserId");
        this.jobType = require(jobType, "jobType");
        this.preset = validatePreset(jobType, preset);
        this.sourceBucket = requireText(sourceBucket, "sourceBucket", BUCKET_LENGTH);
        this.sourceKey = requireText(sourceKey, "sourceKey", KEY_LENGTH);
        this.targetBucket = requireText(targetBucket, "targetBucket", BUCKET_LENGTH);
        this.targetKey = requireText(targetKey, "targetKey", KEY_LENGTH);
        if (this.sourceBucket.equals(this.targetBucket) && this.sourceKey.equals(this.targetKey)) {
            throw new IllegalArgumentException("source and target must be different");
        }
        this.sourceContentType = validateSourceContentType(jobType, sourceContentType);
        this.targetContentType = validateTargetContentType(jobType, targetContentType);
        this.status = MediaEncodeJobStatus.REQUESTED;
        this.requestedAt = require(requestedAt, "requestedAt");
    }

    public static MediaEncodeJob requested(String id, String requestId, Long movieId, Long requestedByUserId,
                                           EncodeJobType jobType, EncodeJobPreset preset,
                                           String sourceBucket, String sourceKey, String targetBucket, String targetKey,
                                           String sourceContentType, String targetContentType, Instant requestedAt) {
        return new MediaEncodeJob(id, requestId, movieId, requestedByUserId, jobType, preset,
                sourceBucket, sourceKey, targetBucket, targetKey, sourceContentType, targetContentType, requestedAt);
    }

    public void markProcessing(Instant time) {
        if (status == MediaEncodeJobStatus.PROCESSING) return;
        requireStatus(MediaEncodeJobStatus.REQUESTED);
        startedAt = requireNotBefore(time, requestedAt, "startedAt", "requestedAt");
        status = MediaEncodeJobStatus.PROCESSING;
    }

    public void markDone(Instant time) {
        if (status == MediaEncodeJobStatus.DONE) return;
        if (status != MediaEncodeJobStatus.REQUESTED && status != MediaEncodeJobStatus.PROCESSING) throw invalidTransition();
        Instant baseline = startedAt == null ? requestedAt : startedAt;
        completedAt = requireNotBefore(time, baseline, "completedAt", startedAt == null ? "requestedAt" : "startedAt");
        status = MediaEncodeJobStatus.DONE;
        failureCode = null;
        failureReason = null;
    }

    public void markFailed(String code, String reason, Instant time) {
        if (status == MediaEncodeJobStatus.FAILED) return;
        if (status != MediaEncodeJobStatus.REQUESTED && status != MediaEncodeJobStatus.PROCESSING) throw invalidTransition();
        Instant baseline = startedAt == null ? requestedAt : startedAt;
        completedAt = requireNotBefore(time, baseline, "completedAt", startedAt == null ? "requestedAt" : "startedAt");
        failureCode = requireText(code, "failureCode", FAILURE_CODE_LENGTH);
        failureReason = requireText(reason, "failureReason", FAILURE_REASON_LENGTH);
        status = MediaEncodeJobStatus.FAILED;
    }

    public void markTimedOut(Instant time) {
        markFailed("ENCODE_TIMEOUT", "Media encoding did not finish before the timeout", time);
    }

    public boolean outputMatches(String bucket, String key, String contentType) {
        return targetBucket.equals(bucket) && targetKey.equals(key) && targetContentType.equals(contentType);
    }

    private void requireStatus(MediaEncodeJobStatus expected) {
        if (status != expected) throw invalidTransition();
    }

    private static InvalidMediaJobStatusTransitionException invalidTransition() {
        return new InvalidMediaJobStatusTransitionException();
    }

    private static EncodeJobPreset validatePreset(EncodeJobType type, EncodeJobPreset preset) {
        EncodeJobPreset required = require(preset, "preset");
        boolean valid = type == EncodeJobType.THUMBNAIL
                ? required == EncodeJobPreset.THUMBNAIL_1280X720
                : required == EncodeJobPreset.VIDEO_HLS_720P_2500K_AAC_96K;
        if (!valid) throw new IllegalArgumentException("preset does not match jobType");
        return required;
    }

    private static String validateSourceContentType(EncodeJobType type, String value) {
        String contentType = requireText(value, "sourceContentType", CONTENT_TYPE_LENGTH);
        boolean valid = type == EncodeJobType.THUMBNAIL ? contentType.startsWith("image/") : contentType.startsWith("video/");
        if (!valid) throw new IllegalArgumentException("sourceContentType does not match jobType");
        return contentType;
    }

    private static String validateTargetContentType(EncodeJobType type, String value) {
        String contentType = requireText(value, "targetContentType", CONTENT_TYPE_LENGTH);
        String expected = type == EncodeJobType.THUMBNAIL ? "image/jpeg" : "application/vnd.apple.mpegurl";
        if (!contentType.equals(expected)) throw new IllegalArgumentException("targetContentType does not match jobType");
        return contentType;
    }

    private static String requireUuid(String value, String field) {
        String text = requireText(value, field, ID_LENGTH);
        try {
            if (!UUID.fromString(text).toString().equals(text)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(field + " must be a canonical UUID");
        }
        return text;
    }

    private static Long requirePositive(Long value, String field) {
        if (value == null || value <= 0) throw new IllegalArgumentException(field + " must be positive");
        return value;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        String trimmed = value.trim();
        if (!trimmed.equals(value)) throw new IllegalArgumentException(field + " must not have surrounding whitespace");
        if (trimmed.length() > maxLength) throw new IllegalArgumentException(field + " must be " + maxLength + " characters or fewer");
        return trimmed;
    }

    private static Instant requireNotBefore(Instant value, Instant baseline, String field, String baselineField) {
        Instant required = require(value, field);
        if (required.isBefore(baseline)) throw new IllegalArgumentException(field + " must not be before " + baselineField);
        return required;
    }

    private static <T> T require(T value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }
}
