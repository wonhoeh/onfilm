package com.onfilm.domain.kafka.service;

import com.onfilm.domain.common.error.exception.MediaEncodeJobNotFoundException;
import com.onfilm.domain.common.error.exception.MediaOutputFileNotFoundException;
import com.onfilm.domain.file.service.StorageKeyPolicy;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.kafka.dto.*;
import com.onfilm.domain.kafka.entity.MediaEncodeJob;
import com.onfilm.domain.kafka.repository.MediaEncodeJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MediaEncodeJobInternalService {
    private final MediaEncodeJobRepository jobRepository;
    private final StorageKeyPolicy storageKeyPolicy;
    private final StorageService storageService;
    private final MediaEncodeJobCompletionTransactionService completionTransactionService;

    @Transactional
    public void markProcessing(String jobId, MediaEncodeProcessingRequest request) {
        MediaEncodeJob job = findJob(jobId);
        job.markProcessing(request.startedAt());
        jobRepository.saveAndFlush(job);
    }

    public void complete(String jobId, MediaEncodeCompletionRequest request) {
        MediaEncodeJobCompletionTransactionService.CompletionInspection inspection =
                completionTransactionService.inspect(jobId, request);
        if (inspection.alreadyCompleted()) {
            return;
        }
        storageKeyPolicy.validateMediaTargetKey(
                inspection.movieId(), inspection.jobType(), inspection.outputKey()
        );
        if (!storageService.exists(inspection.outputKey())) {
            throw new MediaOutputFileNotFoundException();
        }
        completionTransactionService.complete(jobId, request);
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
