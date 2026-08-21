package com.onfilm.domain.kafka.service;

import com.onfilm.domain.common.error.exception.MediaEncodeJobNotFoundException;
import com.onfilm.domain.common.error.exception.MovieNotFoundException;
import com.onfilm.domain.file.service.StorageKeyPolicy;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.kafka.dto.*;
import com.onfilm.domain.kafka.entity.MediaEncodeJob;
import com.onfilm.domain.kafka.repository.MediaEncodeJobRepository;
import com.onfilm.domain.movie.entity.Movie;
import com.onfilm.domain.movie.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MediaEncodeJobInternalService {
    private final MediaEncodeJobRepository jobRepository;
    private final MovieRepository movieRepository;
    private final StorageKeyPolicy storageKeyPolicy;
    private final StorageService storageService;

    @Transactional
    public void markProcessing(String jobId, MediaEncodeProcessingRequest request) {
        MediaEncodeJob job = findJob(jobId);
        job.markProcessing(request.startedAt());
        jobRepository.saveAndFlush(job);
    }

    @Transactional
    public void complete(String jobId, MediaEncodeCompletionRequest request) {
        MediaEncodeJob job = findJob(jobId);
        String bucket = request.outputBucket().trim();
        String key = request.outputKey().trim();
        String contentType = request.contentType().trim();
        if (!job.outputMatches(bucket, key, contentType)) {
            throw new IllegalArgumentException("callback output does not match media encode job");
        }
        if (job.getStatus() == com.onfilm.domain.kafka.entity.MediaEncodeJobStatus.DONE) {
            return;
        }
        if (job.getStatus() == com.onfilm.domain.kafka.entity.MediaEncodeJobStatus.FAILED) {
            throw new IllegalStateException("INVALID_MEDIA_JOB_STATUS_TRANSITION");
        }
        storageKeyPolicy.validateMediaTargetKey(job.getMovieId(), job.getJobType(), key);
        if (!storageService.exists(key)) {
            throw new IllegalArgumentException("encoded output object does not exist");
        }

        Movie movie = movieRepository.findById(job.getMovieId())
                .orElseThrow(() -> new MovieNotFoundException(job.getMovieId()));
        switch (job.getJobType()) {
            case MOVIE -> movie.changeMovieUrl(key);
            case THUMBNAIL -> movie.changeThumbnailUrl(key);
            case TRAILER -> {
                boolean registered = movie.getTrailers().stream()
                        .anyMatch(trailer -> trailer.getStorageKey().equals(key));
                if (!registered) movie.addTrailer(key);
            }
        }
        job.markDone(request.completedAt());
        jobRepository.saveAndFlush(job);
    }

    @Transactional
    public void fail(String jobId, MediaEncodeFailureRequest request) {
        MediaEncodeJob job = findJob(jobId);
        job.markFailed(request.failureCode().trim(), request.failureReason().trim(), request.completedAt());
        jobRepository.saveAndFlush(job);
    }

    /**
     * 구형 Worker의 상태 API 호환용이다. DONE은 결과 반영과 같은 트랜잭션이어야 하므로 허용하지 않는다.
     */
    @Transactional
    public void updateJobStatus(String jobId, MediaJobStatusUpdateRequest request) {
        if (request == null || request.status() == null) throw new IllegalArgumentException("status is required");
        switch (request.status()) {
            case PROCESSING -> {
                if (request.startedAt() == null) throw new IllegalArgumentException("startedAt is required");
                markProcessing(jobId, new MediaEncodeProcessingRequest(request.startedAt()));
            }
            case FAILED -> {
                if (request.completedAt() == null) throw new IllegalArgumentException("completedAt is required");
                fail(jobId, new MediaEncodeFailureRequest(
                        request.failureCode(), request.failureReason(), request.completedAt()));
            }
            case DONE -> throw new IllegalArgumentException("DONE must use the job completion callback");
            case REQUESTED -> throw new IllegalArgumentException("REQUESTED is not updatable via callback");
        }
    }

    private MediaEncodeJob findJob(String jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new MediaEncodeJobNotFoundException(jobId));
    }
}
