package com.onfilm.domain.kafka.service;

import com.onfilm.domain.common.error.exception.InvalidMediaJobStatusTransitionException;
import com.onfilm.domain.common.error.exception.MediaEncodeJobNotFoundException;
import com.onfilm.domain.common.error.exception.MovieNotFoundException;
import com.onfilm.domain.kafka.dto.MediaEncodeCompletionRequest;
import com.onfilm.domain.kafka.entity.MediaEncodeJob;
import com.onfilm.domain.kafka.entity.MediaEncodeJobStatus;
import com.onfilm.domain.kafka.message.EncodeJobType;
import com.onfilm.domain.kafka.repository.MediaEncodeJobRepository;
import com.onfilm.domain.movie.entity.Movie;
import com.onfilm.domain.movie.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MediaEncodeJobCompletionTransactionService {

    private final MediaEncodeJobRepository jobRepository;
    private final MovieRepository movieRepository;

    @Transactional(readOnly = true)
    public CompletionInspection inspect(
            String jobId,
            MediaEncodeCompletionRequest request
    ) {
        MediaEncodeJob job = findJob(jobId);
        CompletionOutput output = validateOutput(job, request);
        validateCompletable(job);
        return new CompletionInspection(
                job.getMovieId(),
                job.getJobType(),
                output.key(),
                job.getStatus() == MediaEncodeJobStatus.DONE
        );
    }

    @Transactional
    public void complete(String jobId, MediaEncodeCompletionRequest request) {
        MediaEncodeJob job = findJob(jobId);
        CompletionOutput output = validateOutput(job, request);
        validateCompletable(job);
        if (job.getStatus() == MediaEncodeJobStatus.DONE) {
            return;
        }

        Movie movie = movieRepository.findById(job.getMovieId())
                .orElseThrow(() -> new MovieNotFoundException(job.getMovieId()));
        applyOutput(movie, job.getJobType(), output.key());
        job.markDone(request.completedAt());
        jobRepository.saveAndFlush(job);
    }

    private MediaEncodeJob findJob(String jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new MediaEncodeJobNotFoundException(jobId));
    }

    private CompletionOutput validateOutput(
            MediaEncodeJob job,
            MediaEncodeCompletionRequest request
    ) {
        String bucket = request.outputBucket().trim();
        String key = request.outputKey().trim();
        String contentType = request.contentType().trim();
        if (!job.outputMatches(bucket, key, contentType)) {
            throw new IllegalArgumentException(
                    "callback output does not match media encode job"
            );
        }
        return new CompletionOutput(key);
    }

    private void validateCompletable(MediaEncodeJob job) {
        if (job.getStatus() == MediaEncodeJobStatus.FAILED) {
            throw new InvalidMediaJobStatusTransitionException();
        }
    }

    private void applyOutput(Movie movie, EncodeJobType jobType, String key) {
        switch (jobType) {
            case MOVIE -> movie.changeMovieUrl(key);
            case THUMBNAIL -> movie.changeThumbnailUrl(key);
            case TRAILER -> {
                boolean registered = movie.getTrailers().stream()
                        .anyMatch(trailer -> trailer.getStorageKey().equals(key));
                if (!registered) {
                    movie.addTrailer(key);
                }
            }
        }
    }

    public record CompletionInspection(
            Long movieId,
            EncodeJobType jobType,
            String outputKey,
            boolean alreadyCompleted
    ) {
    }

    private record CompletionOutput(String key) {
    }
}
