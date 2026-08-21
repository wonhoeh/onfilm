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

    private MediaEncodeJob findJob(String jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new MediaEncodeJobNotFoundException(jobId));
    }
}
