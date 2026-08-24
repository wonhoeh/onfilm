package com.onfilm.domain.kafka.service;

import com.onfilm.domain.common.error.exception.MediaUploadRequestNotFoundException;
import com.onfilm.domain.kafka.dto.PresignedUploadUrlResponse;
import com.onfilm.domain.kafka.entity.MediaUploadRequest;
import com.onfilm.domain.kafka.message.EncodeJobType;
import com.onfilm.domain.kafka.repository.MediaUploadRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaUploadRequestService {
    private final MediaPresignedUploadService presignedUploadService;
    private final MediaUploadRequestRepository repository;
    private final Clock clock;

    @Value("${file.storage.bucket:}")
    private String storageBucket;

    @Transactional
    public PresignedUploadUrlResponse issue(Long userId, Long movieId, EncodeJobType jobType,
                                            String sourceKey, String contentType) {
        if (storageBucket == null || storageBucket.isBlank()) {
            throw new IllegalStateException("file.storage.bucket is required");
        }
        String requestId = extractRequestId(sourceKey);
        PresignedUploadUrlResponse response =
                presignedUploadService.createUploadUrl(requestId, sourceKey, contentType);
        repository.save(MediaUploadRequest.issue(
                requestId, userId, movieId, jobType, storageBucket, sourceKey, contentType,
                clock.instant(), response.expiresAt()
        ));
        return response;
    }

    public String newRequestId() {
        return UUID.randomUUID().toString();
    }

    @Transactional
    public void authorizeRawUpload(Long userId, String sourceKey) {
        String requestId = extractRequestId(sourceKey);
        MediaUploadRequest request = repository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new MediaUploadRequestNotFoundException(requestId));
        request.validateUpload(userId, sourceKey, clock.instant());
    }

    private String extractRequestId(String sourceKey) {
        String fileName = sourceKey.substring(sourceKey.lastIndexOf('/') + 1);
        int extension = fileName.indexOf('.');
        return extension < 0 ? fileName : fileName.substring(0, extension);
    }
}
