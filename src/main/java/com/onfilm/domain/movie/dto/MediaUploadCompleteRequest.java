package com.onfilm.domain.movie.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MediaUploadCompleteRequest(
        @NotBlank(message = "requestId is required")
        @Pattern(regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
                message = "requestId must be a canonical UUID")
        String requestId,
        @NotBlank(message = "sourceKey is required")
        @Size(max = 512, message = "sourceKey must be 512 characters or fewer")
        String sourceKey,
        @NotBlank(message = "contentType is required")
        @Size(max = 128, message = "contentType must be 128 characters or fewer")
        String contentType
) {
}
