package com.onfilm.domain.kafka.service;

import com.onfilm.domain.kafka.dto.PresignedUploadUrlResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;

@Service
@ConditionalOnProperty(name = "file.storage.type", havingValue = "s3")
public class S3MediaPresignedUploadService implements MediaPresignedUploadService {

    private final S3Presigner s3Presigner;
    private final String bucket;
    private final Duration signatureDuration;

    public S3MediaPresignedUploadService(
            S3Presigner s3Presigner,
            @Value("${file.storage.bucket}") String bucket,
            @Value("${file.storage.presign-expiration-minutes:10}") long expirationMinutes
    ) {
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.signatureDuration = Duration.ofMinutes(expirationMinutes);
    }

    @Override
    public PresignedUploadUrlResponse createUploadUrl(String sourceKey, String contentType) {
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(sourceKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(signatureDuration)
                .putObjectRequest(objectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        return new PresignedUploadUrlResponse(
                sourceKey,
                presignedRequest.url().toString(),
                Instant.now().plus(signatureDuration)
        );
    }
}
