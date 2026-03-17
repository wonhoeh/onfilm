package com.onfilm.domain.kafka.service;

import com.onfilm.domain.common.error.exception.MediaEncodeJobNotFoundException;
import com.onfilm.domain.common.error.exception.MovieNotFoundException;
import com.onfilm.domain.kafka.dto.MediaJobStatusUpdateRequest;
import com.onfilm.domain.kafka.dto.MovieMediaUpdateRequest;
import com.onfilm.domain.kafka.dto.TrailerMediaUpdateRequest;
import com.onfilm.domain.kafka.entity.MediaEncodeJob;
import com.onfilm.domain.kafka.entity.MediaEncodeJobStatus;
import com.onfilm.domain.kafka.repository.MediaEncodeJobRepository;
import com.onfilm.domain.movie.entity.Movie;
import com.onfilm.domain.movie.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MediaEncodeJobInternalService {

    private final MediaEncodeJobRepository mediaEncodeJobRepository;
    private final MovieRepository movieRepository;

    @Transactional
    public void updateJobStatus(String jobId, MediaJobStatusUpdateRequest request) {
        if (request == null || request.status() == null) {
            throw new IllegalArgumentException("status is required");
        }

        MediaEncodeJob job = mediaEncodeJobRepository.findById(jobId)
                .orElseThrow(() -> new MediaEncodeJobNotFoundException(jobId));

        switch (request.status()) {
            case PROCESSING -> {
                if (request.startedAt() == null) {
                    throw new IllegalArgumentException("startedAt is required");
                }
                job.markProcessing(request.startedAt());
            }
            case DONE -> {
                if (request.completedAt() == null) {
                    throw new IllegalArgumentException("completedAt is required");
                }
                job.markDone(request.completedAt());
            }
            case FAILED -> {
                if (request.completedAt() == null) {
                    throw new IllegalArgumentException("completedAt is required");
                }
                if (request.failureReason() == null || request.failureReason().isBlank()) {
                    throw new IllegalArgumentException("failureReason is required");
                }
                job.markFailed(request.failureReason().trim(), request.completedAt());
            }
            case REQUESTED -> throw new IllegalArgumentException("REQUESTED is not updatable via callback");
        }
    }

    @Transactional
    public void updateMovieMedia(Long movieId, MovieMediaUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }

        boolean hasVideo = request.videoUrl() != null && !request.videoUrl().isBlank();
        boolean hasThumbnail = request.thumbnailUrl() != null && !request.thumbnailUrl().isBlank();
        if (!hasVideo && !hasThumbnail) {
            throw new IllegalArgumentException("videoUrl or thumbnailUrl is required");
        }

        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException(movieId));

        if (hasVideo) {
            movie.changeMovieUrl(request.videoUrl().trim());
        }
        if (hasThumbnail) {
            movie.changeThumbnailUrl(request.thumbnailUrl().trim());
        }
    }

    @Transactional
    public void updateTrailerMedia(String jobId, TrailerMediaUpdateRequest request) {
        if (request == null || request.trailerUrl() == null || request.trailerUrl().isBlank()) {
            throw new IllegalArgumentException("trailerUrl is required");
        }

        MediaEncodeJob job = mediaEncodeJobRepository.findById(jobId)
                .orElseThrow(() -> new MediaEncodeJobNotFoundException(jobId));
        if (job.getJobType() != com.onfilm.domain.kafka.message.EncodeJobType.TRAILER) {
            throw new IllegalArgumentException("job type must be TRAILER");
        }

        Movie movie = movieRepository.findById(job.getMovieId())
                .orElseThrow(() -> new MovieNotFoundException(job.getMovieId()));
        movie.addTrailer(request.trailerUrl().trim());
    }
}
