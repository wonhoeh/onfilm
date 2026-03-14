package com.onfilm.domain.kafka.dto;

import com.onfilm.domain.kafka.entity.MediaEncodeJob;
import com.onfilm.domain.kafka.entity.MediaEncodeJobStatus;
import com.onfilm.domain.kafka.message.EncodeJobPreset;
import com.onfilm.domain.kafka.message.EncodeJobType;

import java.time.Instant;

public record MediaEncodeJobStatusResponse(
        String jobId,
        Long movieId,
        EncodeJobType jobType,
        EncodeJobPreset preset,
        String sourceKey,
        String targetKey,
        MediaEncodeJobStatus status,
        Instant requestedAt,
        Instant startedAt,
        Instant completedAt,
        String failureReason
) {
    public static MediaEncodeJobStatusResponse from(MediaEncodeJob job) {
        return new MediaEncodeJobStatusResponse(
                job.getId(),
                job.getMovieId(),
                job.getJobType(),
                job.getPreset(),
                job.getSourceKey(),
                job.getTargetKey(),
                job.getStatus(),
                job.getRequestedAt(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getFailureReason()
        );
    }
}
