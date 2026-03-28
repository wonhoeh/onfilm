package com.onfilm.domain.kafka.entity;

import com.onfilm.domain.kafka.message.EncodeJobPreset;
import com.onfilm.domain.kafka.message.EncodeJobType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaEncodeJob {

    @Id
    @Column(nullable = false, length = 64)
    private String id;

    @Column(nullable = false)
    private Long movieId;

    @Column(nullable = false)
    private Long requestedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EncodeJobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private EncodeJobPreset preset;

    @Column(nullable = false)
    private String sourceBucket;

    @Column(nullable = false)
    private String sourceKey;

    @Column(nullable = false)
    private String targetBucket;

    @Column(nullable = false)
    private String targetKey;

    @Column
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MediaEncodeJobStatus status;

    @Column(nullable = false)
    private Instant requestedAt;

    @Column
    private Instant startedAt;

    @Column
    private Instant completedAt;

    @Column(length = 1000)
    private String failureReason;

    // Kafka 발행 직후 저장하는 작업 스냅샷.
    public static MediaEncodeJob requested(
            String id,
            Long movieId,
            Long requestedByUserId,
            EncodeJobType jobType,
            EncodeJobPreset preset,
            String sourceBucket,
            String sourceKey,
            String targetBucket,
            String targetKey,
            String contentType,
            Instant requestedAt
    ) {
        MediaEncodeJob job = new MediaEncodeJob();
        job.id = id;
        job.movieId = movieId;
        job.requestedByUserId = requestedByUserId;
        job.jobType = jobType;
        job.preset = preset;
        job.sourceBucket = sourceBucket;
        job.sourceKey = sourceKey;
        job.targetBucket = targetBucket;
        job.targetKey = targetKey;
        job.contentType = contentType;
        job.status = MediaEncodeJobStatus.REQUESTED;
        job.requestedAt = requestedAt;
        return job;
    }

    public void markProcessing(Instant startedAt) {
        if (this.status != MediaEncodeJobStatus.REQUESTED) {
            throw new IllegalStateException("INVALID_MEDIA_JOB_STATUS_TRANSITION");
        }
        this.status = MediaEncodeJobStatus.PROCESSING;
        this.startedAt = startedAt;
        this.completedAt = null;
        this.failureReason = null;
    }

    public void markDone(Instant completedAt) {
        if (this.status == MediaEncodeJobStatus.DONE) {
            return;
        }
        if (this.status != MediaEncodeJobStatus.PROCESSING) {
            throw new IllegalStateException("INVALID_MEDIA_JOB_STATUS_TRANSITION");
        }
        this.status = MediaEncodeJobStatus.DONE;
        this.completedAt = completedAt;
        this.failureReason = null;
    }

    public void markFailed(String failureReason, Instant completedAt) {
        if (this.status == MediaEncodeJobStatus.FAILED) {
            return;
        }
        if (this.status != MediaEncodeJobStatus.REQUESTED && this.status != MediaEncodeJobStatus.PROCESSING) {
            throw new IllegalStateException("INVALID_MEDIA_JOB_STATUS_TRANSITION");
        }
        this.status = MediaEncodeJobStatus.FAILED;
        this.completedAt = completedAt;
        this.failureReason = failureReason;
    }
}
