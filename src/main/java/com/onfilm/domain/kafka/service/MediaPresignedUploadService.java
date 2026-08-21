package com.onfilm.domain.kafka.service;

import com.onfilm.domain.kafka.dto.PresignedUploadUrlResponse;

public interface MediaPresignedUploadService {

    PresignedUploadUrlResponse createUploadUrl(String requestId, String sourceKey, String contentType);
}
