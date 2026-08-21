package com.onfilm.domain.kafka.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PresignUploadRequest(
        @NotBlank(message = "contentType is required")
        @Size(max = 128, message = "contentType must be 128 characters or fewer")
        String contentType
) {
}
