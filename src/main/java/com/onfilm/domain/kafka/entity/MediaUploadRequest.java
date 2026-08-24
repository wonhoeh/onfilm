package com.onfilm.domain.kafka.entity;

import com.onfilm.domain.common.error.exception.ForbiddenMediaUploadAccessException;
import com.onfilm.domain.common.error.exception.MediaUploadAlreadyCompletedException;
import com.onfilm.domain.common.error.exception.MediaUploadRequestExpiredException;
import com.onfilm.domain.kafka.message.EncodeJobType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_upload_requests",
        indexes = {
                @Index(name = "idx_media_upload_user", columnList = "requested_by_user_id"),
                @Index(name = "idx_media_upload_status_expires", columnList = "status,expires_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaUploadRequest {
    @Id
    @Column(nullable = false, updatable = false, length = MediaEncodeJob.ID_LENGTH)
    private String id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "requested_by_user_id", nullable = false, updatable = false)
    private Long requestedByUserId;

    @Column(name = "movie_id", nullable = false, updatable = false)
    private Long movieId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, updatable = false, length = 32)
    private EncodeJobType jobType;

    @Column(nullable = false, updatable = false, length = MediaEncodeJob.BUCKET_LENGTH)
    private String bucket;

    @Column(name = "source_key", nullable = false, updatable = false, length = MediaEncodeJob.KEY_LENGTH)
    private String sourceKey;

    @Column(name = "content_type", nullable = false, updatable = false, length = MediaEncodeJob.CONTENT_TYPE_LENGTH)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MediaUploadRequestStatus status;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "job_id", length = MediaEncodeJob.ID_LENGTH)
    private String jobId;

    @Column(name = "completed_at")
    private Instant completedAt;

    private MediaUploadRequest(String id, Long userId, Long movieId, EncodeJobType jobType,
                               String bucket, String sourceKey, String contentType,
                               Instant issuedAt, Instant expiresAt) {
        this.id = requireUuid(id);
        this.requestedByUserId = requirePositive(userId, "requestedByUserId");
        this.movieId = requirePositive(movieId, "movieId");
        this.jobType = require(jobType, "jobType");
        this.bucket = requireText(bucket, "bucket");
        this.sourceKey = requireText(sourceKey, "sourceKey");
        this.contentType = requireText(contentType, "contentType");
        this.issuedAt = require(issuedAt, "issuedAt");
        this.expiresAt = require(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(issuedAt)) throw new IllegalArgumentException("expiresAt must be after issuedAt");
        this.status = MediaUploadRequestStatus.ISSUED;
    }

    public static MediaUploadRequest issue(String id, Long userId, Long movieId, EncodeJobType jobType,
                                           String bucket, String sourceKey, String contentType,
                                           Instant issuedAt, Instant expiresAt) {
        return new MediaUploadRequest(id, userId, movieId, jobType, bucket, sourceKey, contentType, issuedAt, expiresAt);
    }

    public void validateCompletion(Long userId, Long movieId, EncodeJobType jobType,
                                   String sourceKey, String contentType, Instant now) {
        if (!requestedByUserId.equals(userId) || !this.movieId.equals(movieId)) {
            throw new ForbiddenMediaUploadAccessException();
        }
        if (this.jobType != jobType || !this.sourceKey.equals(sourceKey) || !this.contentType.equals(contentType)) {
            throw new IllegalArgumentException("upload completion does not match presign request");
        }
        if (status == MediaUploadRequestStatus.COMPLETED) return;
        if (status == MediaUploadRequestStatus.EXPIRED || !now.isBefore(expiresAt)) {
            status = MediaUploadRequestStatus.EXPIRED;
            throw new MediaUploadRequestExpiredException();
        }
    }

    public void validateUpload(Long userId, String sourceKey, Instant now) {
        if (!requestedByUserId.equals(userId)) throw new ForbiddenMediaUploadAccessException();
        if (!this.sourceKey.equals(sourceKey)) throw new IllegalArgumentException("sourceKey does not match upload request");
        if (status != MediaUploadRequestStatus.ISSUED || !now.isBefore(expiresAt)) {
            if (status == MediaUploadRequestStatus.ISSUED) status = MediaUploadRequestStatus.EXPIRED;
            throw new MediaUploadRequestExpiredException();
        }
    }

    public void complete(String jobId, Instant completedAt) {
        if (status == MediaUploadRequestStatus.COMPLETED) {
            if (!this.jobId.equals(jobId)) throw new MediaUploadAlreadyCompletedException();
            return;
        }
        if (status != MediaUploadRequestStatus.ISSUED) throw new MediaUploadRequestExpiredException();
        this.jobId = requireUuid(jobId);
        this.completedAt = require(completedAt, "completedAt");
        this.status = MediaUploadRequestStatus.COMPLETED;
    }

    private static String requireUuid(String value) {
        String text = requireText(value, "id");
        try {
            if (!UUID.fromString(text).toString().equals(text)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("id must be a canonical UUID");
        }
        return text;
    }

    private static Long requirePositive(Long value, String field) {
        if (value == null || value <= 0) throw new IllegalArgumentException(field + " must be positive");
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    private static <T> T require(T value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }
}
