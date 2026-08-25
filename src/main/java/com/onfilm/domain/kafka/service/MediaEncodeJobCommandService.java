package com.onfilm.domain.kafka.service;

import com.onfilm.domain.common.error.exception.MediaSourceFileNotFoundException;
import com.onfilm.domain.file.service.StorageKeyPolicy;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.kafka.message.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class MediaEncodeJobCommandService {
    private static final String HLS_CONTENT_TYPE = "application/vnd.apple.mpegurl";
    private static final String JPEG_CONTENT_TYPE = "image/jpeg";

    private final MediaEncodeJobTransactionService transactionService;
    private final StorageService storageService;
    private final StorageKeyPolicy storageKeyPolicy;
    private final Clock clock;

    public String requestMovieEncoding(String requestId, Long movieId, Long userId,
                                       String sourceBucket, String sourceKey,
                                       String targetBucket, String targetKey, String contentType) {
        return request(requestId, movieId, userId, EncodeJobType.MOVIE,
                EncodeJobPreset.VIDEO_HLS_720P_2500K_AAC_96K,
                sourceBucket, sourceKey, targetBucket, targetKey, contentType, HLS_CONTENT_TYPE);
    }

    public String requestTrailerEncoding(String requestId, Long movieId, Long userId,
                                         String sourceBucket, String sourceKey,
                                         String targetBucket, String targetKey, String contentType) {
        return request(requestId, movieId, userId, EncodeJobType.TRAILER,
                EncodeJobPreset.VIDEO_HLS_720P_2500K_AAC_96K,
                sourceBucket, sourceKey, targetBucket, targetKey, contentType, HLS_CONTENT_TYPE);
    }

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
        Instant now = clock.instant();
        MediaEncodeJobTransactionService.JobRequest request =
                new MediaEncodeJobTransactionService.JobRequest(
                        requestId, movieId, userId, jobType, preset,
                        sourceBucket, sourceKey, targetBucket, targetKey,
                        sourceContentType, targetContentType
                );
        var existingJobId = transactionService.findExistingJob(request, now);
        if (existingJobId.isPresent()) {
            return existingJobId.get();
        }

        storageKeyPolicy.validateMediaSourceKey(movieId, requestId, jobType, sourceKey);
        storageKeyPolicy.validateMediaTargetKey(movieId, jobType, targetKey);
        if (!storageService.exists(sourceKey)) {
            throw new MediaSourceFileNotFoundException();
        }
        return transactionService.createJob(request, now);
    }
}
