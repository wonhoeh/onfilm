package com.onfilm.domain.kafka.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record MediaEncodeCompletionRequest(
        @NotBlank @Size(max = 63) String outputBucket,
        @NotBlank @Size(max = 512) String outputKey,
        @NotBlank @Size(max = 128) String contentType,
        @NotNull Instant completedAt
) {
}
