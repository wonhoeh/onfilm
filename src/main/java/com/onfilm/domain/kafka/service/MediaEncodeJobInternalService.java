package com.onfilm.domain.kafka.service;

import com.onfilm.domain.common.error.exception.MediaEncodeJobNotFoundException;
import com.onfilm.domain.common.error.exception.MediaOutputFileNotFoundException;
import com.onfilm.domain.common.error.exception.InvalidMediaJobStatusTransitionException;
import com.onfilm.domain.file.service.StorageKeyPolicy;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.kafka.dto.*;
import com.onfilm.domain.kafka.entity.MediaEncodeJob;
import com.onfilm.domain.kafka.entity.MediaEncodeJobStatus;
import com.onfilm.domain.kafka.metrics.MediaEncodeMetrics;
import com.onfilm.domain.kafka.repository.MediaEncodeJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class MediaEncodeJobInternalService {
    private final MediaEncodeJobRepository jobRepository;
    private final StorageKeyPolicy storageKeyPolicy;
    private final StorageService storageService;
    private final MediaEncodeJobCompletionTransactionService completionTransactionService;
    private final MediaEncodeMetrics metrics;

    @Transactional
    public void markProcessing(String jobId, MediaEncodeProcessingRequest request) {
        long startedAt = System.nanoTime();
        String result = "error";
        try {
            MediaEncodeJob job = findJob(jobId);
            MediaEncodeJobStatus previous = job.getStatus();
            job.markProcessing(request.startedAt());
            jobRepository.saveAndFlush(job);
            result = previous == MediaEncodeJobStatus.PROCESSING ? "duplicate" : "applied";
            if (result.equals("applied")) {
                metrics.recordJobTransition(job.getJobType(), MediaEncodeJobStatus.PROCESSING);
            }
        } catch (InvalidMediaJobStatusTransitionException exception) {
            result = "conflict";
            throw exception;
        } finally {
            metrics.recordCallback("processing", result, System.nanoTime() - startedAt);
        }
    }

    public void complete(String jobId, MediaEncodeCompletionRequest request) {
        long startedAt = System.nanoTime();
        String result = "error";
        try {
            MediaEncodeJobCompletionTransactionService.CompletionInspection inspection =
                    completionTransactionService.inspect(jobId, request);
            if (inspection.alreadyCompleted()) {
                result = "duplicate";
                return;
            }
            storageKeyPolicy.validateMediaTargetKey(
                    inspection.movieId(), inspection.jobType(), inspection.outputKey()
            );
            if (!storageService.exists(inspection.outputKey())) {
                throw new MediaOutputFileNotFoundException();
            }
            boolean completed = completionTransactionService.complete(jobId, request);
            result = completed ? "applied" : "duplicate";
            if (completed) {
                metrics.recordJobTransition(inspection.jobType(), MediaEncodeJobStatus.DONE);
                metrics.recordJobTerminal(
                        inspection.jobType(),
                        "success",
                        Duration.between(inspection.requestedAt(), request.completedAt())
                );
            }
        } catch (InvalidMediaJobStatusTransitionException exception) {
            result = "conflict";
            throw exception;
        } finally {
            metrics.recordCallback("complete", result, System.nanoTime() - startedAt);
        }
    }

    @Transactional
    public void fail(String jobId, MediaEncodeFailureRequest request) {
        long startedAt = System.nanoTime();
        String result = "error";
        try {
            MediaEncodeJob job = findJob(jobId);
            MediaEncodeJobStatus previous = job.getStatus();
            job.markFailed(request.failureCode().trim(), request.failureReason().trim(), request.completedAt());
            jobRepository.saveAndFlush(job);
            result = previous == MediaEncodeJobStatus.FAILED ? "duplicate" : "applied";
            if (result.equals("applied")) {
                metrics.recordJobTransition(job.getJobType(), MediaEncodeJobStatus.FAILED);
                metrics.recordJobTerminal(
                        job.getJobType(),
                        "failure",
                        Duration.between(job.getRequestedAt(), job.getCompletedAt())
                );
            }
        } catch (InvalidMediaJobStatusTransitionException exception) {
            result = "conflict";
            throw exception;
        } finally {
            metrics.recordCallback("fail", result, System.nanoTime() - startedAt);
        }
    }

    private MediaEncodeJob findJob(String jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new MediaEncodeJobNotFoundException(jobId));
    }
}
