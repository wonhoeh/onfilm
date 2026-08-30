package com.onfilm.domain.kafka.service;

import com.onfilm.domain.kafka.entity.MediaEncodeJob;
import com.onfilm.domain.kafka.entity.MediaEncodeJobStatus;
import com.onfilm.domain.kafka.entity.MediaEncodeOutboxStatus;
import com.onfilm.domain.kafka.entity.MediaUploadRequestStatus;
import com.onfilm.domain.kafka.metrics.MediaEncodeMetrics;
import com.onfilm.domain.kafka.repository.MediaEncodeJobRepository;
import com.onfilm.domain.kafka.repository.MediaEncodeOutboxRepository;
import com.onfilm.domain.kafka.repository.MediaUploadRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaEncodeMaintenanceService {
    private final MediaEncodeJobRepository jobRepository;
    private final MediaEncodeOutboxRepository outboxRepository;
    private final MediaUploadRequestRepository uploadRequestRepository;
    private final Clock clock;
    private final MediaEncodeMetrics metrics;

    @Value("${media-encode.job-timeout:PT4H30M}")
    private Duration jobTimeout;

    @Value("${media-encode.outbox-retention:P7D}")
    private Duration outboxRetention;

    @Value("${media-encode.job-retention:P30D}")
    private Duration jobRetention;

    @Scheduled(fixedDelayString = "${media-encode.timeout-check-delay:60000}")
    @Transactional
    public int failTimedOutJobs() {
        Instant now = clock.instant();
        List<MediaEncodeJob> jobs = jobRepository.findTop100ByStatusInAndRequestedAtBefore(
                List.of(MediaEncodeJobStatus.REQUESTED, MediaEncodeJobStatus.PROCESSING),
                now.minus(requirePositive(jobTimeout, "job timeout"))
        );
        jobs.forEach(job -> {
            job.markTimedOut(now);
            metrics.recordJobTimeout(
                    job.getJobType(),
                    Duration.between(job.getRequestedAt(), now)
            );
        });
        if (!jobs.isEmpty()) log.warn("Marked {} media encode jobs as timed out", jobs.size());
        return jobs.size();
    }

    @Scheduled(cron = "${media-encode.outbox-cleanup-cron:0 30 4 * * *}")
    @Transactional
    public int cleanupPublishedOutbox() {
        int deleted = outboxRepository.deletePublishedBefore(
                MediaEncodeOutboxStatus.PUBLISHED,
                clock.instant().minus(requirePositive(outboxRetention, "outbox retention"))
        );
        int deletedJobs = jobRepository.deleteTerminalBefore(
                List.of(MediaEncodeJobStatus.DONE, MediaEncodeJobStatus.FAILED),
                clock.instant().minus(requirePositive(jobRetention, "job retention"))
        );
        int deletedUploads = uploadRequestRepository.deleteExpiredBefore(
                MediaUploadRequestStatus.COMPLETED,
                clock.instant(),
                clock.instant().minus(requirePositive(jobRetention, "job retention"))
        );
        if (deleted + deletedJobs + deletedUploads > 0) {
            log.info("Cleaned media encode history. outbox={}, jobs={}, uploads={}",
                    deleted, deletedJobs, deletedUploads);
        }
        return deleted + deletedJobs + deletedUploads;
    }

    private Duration requirePositive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(field + " must be positive");
        }
        return value;
    }
}
