package com.onfilm.domain.kafka.metrics;

import com.onfilm.domain.kafka.entity.MediaEncodeJobStatus;
import com.onfilm.domain.kafka.entity.MediaEncodeOutboxStatus;
import com.onfilm.domain.kafka.repository.MediaEncodeJobRepository;
import com.onfilm.domain.kafka.repository.MediaEncodeOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class MediaEncodeMetricSnapshotService {
    private final MediaEncodeOutboxRepository outboxRepository;
    private final MediaEncodeJobRepository jobRepository;
    private final MediaEncodeMetrics metrics;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${media-encode.metrics-snapshot-delay:30000}")
    @Transactional(readOnly = true)
    public void refresh() {
        for (MediaEncodeOutboxStatus status : MediaEncodeOutboxStatus.values()) {
            metrics.updateOutboxCount(status, outboxRepository.countByStatus(status));
        }
        for (MediaEncodeJobStatus status : MediaEncodeJobStatus.values()) {
            metrics.updateJobCount(status, jobRepository.countByStatus(status));
        }

        Instant now = clock.instant();
        Duration oldestPendingAge = outboxRepository
                .findOldestCreatedAtByStatus(MediaEncodeOutboxStatus.PENDING)
                .map(createdAt -> Duration.between(createdAt, now))
                .orElse(Duration.ZERO);
        metrics.updateOldestPendingOutboxAge(oldestPendingAge);
    }
}
