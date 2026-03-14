package com.onfilm.domain.kafka.service;

import com.onfilm.domain.kafka.dto.PresignedUploadUrlResponse;

public interface MediaPresignedUploadService {

    PresignedUploadUrlResponse createUploadUrl(String sourceKey, String contentType);
}
