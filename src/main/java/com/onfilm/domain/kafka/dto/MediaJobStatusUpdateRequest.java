package com.onfilm.domain.kafka.dto;

import com.onfilm.domain.kafka.entity.MediaEncodeJobStatus;

import java.time.Instant;

public record MediaJobStatusUpdateRequest(
        MediaEncodeJobStatus status,
        Instant startedAt,
        Instant completedAt,
        String failureReason
) {
}
