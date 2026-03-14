package com.onfilm.domain.kafka.dto;

import java.time.Instant;

public record PresignedUploadUrlResponse(
        String sourceKey,
        String uploadUrl,
        Instant expiresAt
) {
}
