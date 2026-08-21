package com.onfilm.domain.kafka.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record MediaEncodeProcessingRequest(@NotNull Instant startedAt) {
}
