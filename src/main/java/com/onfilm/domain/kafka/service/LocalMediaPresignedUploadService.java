package com.onfilm.domain.kafka.service;

import com.onfilm.domain.kafka.dto.PresignedUploadUrlResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;

@Service
@ConditionalOnProperty(name = "file.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalMediaPresignedUploadService implements MediaPresignedUploadService {

    private final Duration signatureDuration;

    public LocalMediaPresignedUploadService(
            @Value("${file.storage.presign-expiration-minutes:10}") long expirationMinutes
    ) {
        this.signatureDuration = Duration.ofMinutes(expirationMinutes);
    }

    @Override
    public PresignedUploadUrlResponse createUploadUrl(String sourceKey, String contentType) {
        String uploadUrl = UriComponentsBuilder.fromPath("/api/files/movie/raw-upload")
                .queryParam("sourceKey", sourceKey)
                .build()
                .toUriString();

        return new PresignedUploadUrlResponse(
                sourceKey,
                uploadUrl,
                Instant.now().plus(signatureDuration)
        );
    }
}
