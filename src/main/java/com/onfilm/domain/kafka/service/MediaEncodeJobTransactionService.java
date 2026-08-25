package com.onfilm.domain.kafka.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onfilm.domain.common.error.exception.MediaEncodeJobNotFoundException;
import com.onfilm.domain.common.error.exception.MediaUploadRequestMismatchException;
import com.onfilm.domain.common.error.exception.MediaUploadRequestNotFoundException;
import com.onfilm.domain.kafka.entity.MediaEncodeJob;
import com.onfilm.domain.kafka.entity.MediaEncodeOutbox;
import com.onfilm.domain.kafka.entity.MediaUploadRequest;
import com.onfilm.domain.kafka.message.EncodeJobPreset;
import com.onfilm.domain.kafka.message.EncodeJobType;
import com.onfilm.domain.kafka.message.MediaEncodeRequestedMessage;
import com.onfilm.domain.kafka.repository.MediaEncodeJobRepository;
import com.onfilm.domain.kafka.repository.MediaEncodeOutboxRepository;
import com.onfilm.domain.kafka.repository.MediaUploadRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaEncodeJobTransactionService {

    private final MediaEncodeJobRepository jobRepository;
    private final MediaUploadRequestRepository uploadRequestRepository;
    private final MediaEncodeOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Optional<String> findExistingJob(JobRequest request, Instant now) {
        MediaUploadRequest upload = uploadRequestRepository.findById(request.requestId())
                .orElseThrow(() -> new MediaUploadRequestNotFoundException(request.requestId()));
        validateCompletion(upload, request, now);
        Optional<String> existingJobId = findExistingJob(upload);
        if (existingJobId.isEmpty()) {
            validateSourceBucket(upload, request);
        }
        return existingJobId;
    }

    @Transactional
    public String createJob(JobRequest request, Instant now) {
        MediaUploadRequest upload = uploadRequestRepository.findByIdForUpdate(request.requestId())
                .orElseThrow(() -> new MediaUploadRequestNotFoundException(request.requestId()));
        validateCompletion(upload, request, now);

        Optional<String> existingJobId = findExistingJob(upload);
        if (existingJobId.isPresent()) {
            return existingJobId.get();
        }
        validateSourceBucket(upload, request);

        String jobId = UUID.randomUUID().toString();
        MediaEncodeJob job = MediaEncodeJob.requested(
                jobId, request.requestId(), request.movieId(), request.userId(),
                request.jobType(), request.preset(), request.sourceBucket(),
                request.sourceKey(), request.targetBucket(), request.targetKey(),
                request.sourceContentType(), request.targetContentType(), now
        );
        MediaEncodeRequestedMessage message = new MediaEncodeRequestedMessage(
                MediaEncodeRequestedMessage.CURRENT_SCHEMA_VERSION,
                jobId, request.requestId(), request.movieId(), request.userId(),
                request.jobType(), request.preset(), request.sourceBucket(),
                request.sourceKey(), request.targetBucket(), request.targetKey(),
                request.sourceContentType(), request.targetContentType(), now
        );

        jobRepository.save(job);
        outboxRepository.save(MediaEncodeOutbox.pending(
                UUID.randomUUID().toString(), jobId, serialize(message), now
        ));
        upload.complete(jobId, now);
        return jobId;
    }

    private void validateCompletion(
            MediaUploadRequest upload,
            JobRequest request,
            Instant now
    ) {
        upload.validateCompletion(
                request.userId(), request.movieId(), request.jobType(),
                request.sourceKey(), request.sourceContentType(), now
        );
    }

    private void validateSourceBucket(
            MediaUploadRequest upload,
            JobRequest request
    ) {
        if (!upload.getBucket().equals(request.sourceBucket())) {
            throw new MediaUploadRequestMismatchException();
        }
    }

    private Optional<String> findExistingJob(MediaUploadRequest upload) {
        if (upload.getJobId() == null) {
            return Optional.empty();
        }
        String jobId = upload.getJobId();
        return Optional.of(jobRepository.findById(jobId)
                .orElseThrow(() -> new MediaEncodeJobNotFoundException(jobId))
                .getId());
    }

    private String serialize(MediaEncodeRequestedMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "MEDIA_ENCODE_MESSAGE_SERIALIZATION_FAILED",
                    exception
            );
        }
    }

    public record JobRequest(
            String requestId,
            Long movieId,
            Long userId,
            EncodeJobType jobType,
            EncodeJobPreset preset,
            String sourceBucket,
            String sourceKey,
            String targetBucket,
            String targetKey,
            String sourceContentType,
            String targetContentType
    ) {
    }
}
