package com.onfilm.domain.kafka.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record MediaEncodeFailureRequest(
        @NotBlank @Size(max = 64) String failureCode,
        @NotBlank @Size(max = 1000) String failureReason,
        @NotNull Instant completedAt
) {
}
