package com.onfilm.domain.kafka.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onfilm.domain.common.error.exception.MediaEncodeJobNotFoundException;
import com.onfilm.domain.common.error.exception.MediaUploadRequestNotFoundException;
import com.onfilm.domain.file.service.StorageKeyPolicy;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.kafka.entity.*;
import com.onfilm.domain.kafka.message.*;
import com.onfilm.domain.kafka.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaEncodeJobCommandService {
    private static final String HLS_CONTENT_TYPE = "application/vnd.apple.mpegurl";
    private static final String JPEG_CONTENT_TYPE = "image/jpeg";

    private final MediaEncodeJobRepository jobRepository;
    private final MediaUploadRequestRepository uploadRequestRepository;
    private final MediaEncodeOutboxRepository outboxRepository;
    private final StorageService storageService;
    private final StorageKeyPolicy storageKeyPolicy;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public String requestMovieEncoding(String requestId, Long movieId, Long userId,
                                       String sourceBucket, String sourceKey,
                                       String targetBucket, String targetKey, String contentType) {
        return request(requestId, movieId, userId, EncodeJobType.MOVIE,
                EncodeJobPreset.VIDEO_HLS_720P_2500K_AAC_96K,
                sourceBucket, sourceKey, targetBucket, targetKey, contentType, HLS_CONTENT_TYPE);
    }

    @Transactional
    public String requestTrailerEncoding(String requestId, Long movieId, Long userId,
                                         String sourceBucket, String sourceKey,
                                         String targetBucket, String targetKey, String contentType) {
        return request(requestId, movieId, userId, EncodeJobType.TRAILER,
                EncodeJobPreset.VIDEO_HLS_720P_2500K_AAC_96K,
                sourceBucket, sourceKey, targetBucket, targetKey, contentType, HLS_CONTENT_TYPE);
    }

    @Transactional
    public String requestThumbnailEncoding(String requestId, Long movieId, Long userId,
                                           String sourceBucket, String sourceKey,
                                           String targetBucket, String targetKey, String contentType) {
        return request(requestId, movieId, userId, EncodeJobType.THUMBNAIL,
                EncodeJobPreset.THUMBNAIL_1280X720,
                sourceBucket, sourceKey, targetBucket, targetKey, contentType, JPEG_CONTENT_TYPE);
    }

    private String request(String requestId, Long movieId, Long userId,
                           EncodeJobType jobType, EncodeJobPreset preset,
                           String sourceBucket, String sourceKey,
                           String targetBucket, String targetKey,
                           String sourceContentType, String targetContentType) {
        MediaUploadRequest upload = uploadRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new MediaUploadRequestNotFoundException(requestId));
        Instant now = clock.instant();
        upload.validateCompletion(userId, movieId, jobType, sourceKey, sourceContentType, now);

        if (upload.getJobId() != null) {
            return jobRepository.findById(upload.getJobId())
                    .orElseThrow(() -> new MediaEncodeJobNotFoundException(upload.getJobId()))
                    .getId();
        }

        if (!upload.getBucket().equals(sourceBucket)) {
            throw new IllegalArgumentException("sourceBucket does not match presign request");
        }
        storageKeyPolicy.validateMediaSourceKey(movieId, requestId, jobType, sourceKey);
        storageKeyPolicy.validateMediaTargetKey(movieId, jobType, targetKey);
        if (!storageService.exists(sourceKey)) {
            throw new IllegalArgumentException("uploaded source object does not exist");
        }

        String jobId = UUID.randomUUID().toString();
        MediaEncodeJob job = MediaEncodeJob.requested(
                jobId, requestId, movieId, userId, jobType, preset,
                sourceBucket, sourceKey, targetBucket, targetKey,
                sourceContentType, targetContentType, now
        );
        MediaEncodeRequestedMessage message = new MediaEncodeRequestedMessage(
                MediaEncodeRequestedMessage.CURRENT_SCHEMA_VERSION,
                jobId, requestId, movieId, userId, jobType, preset,
                sourceBucket, sourceKey, targetBucket, targetKey,
                sourceContentType, targetContentType, now
        );

        jobRepository.save(job);
        outboxRepository.save(MediaEncodeOutbox.pending(
                UUID.randomUUID().toString(), jobId, serialize(message), now));
        upload.complete(jobId, now);
        return jobId;
    }

    private String serialize(MediaEncodeRequestedMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("MEDIA_ENCODE_MESSAGE_SERIALIZATION_FAILED", e);
        }
    }
}
