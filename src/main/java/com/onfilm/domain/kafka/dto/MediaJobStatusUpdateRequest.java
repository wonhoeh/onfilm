package com.onfilm.domain.kafka.dto;

import com.onfilm.domain.kafka.entity.MediaEncodeJobStatus;

import java.time.Instant;
import jakarta.validation.constraints.Size;

public record MediaJobStatusUpdateRequest(
        MediaEncodeJobStatus status,
        Instant startedAt,
        Instant completedAt,
        @Size(max = 64)
        String failureCode,
        @Size(max = 1000)
        String failureReason
) {
}
