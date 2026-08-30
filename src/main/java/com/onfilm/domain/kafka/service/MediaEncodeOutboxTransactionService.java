package com.onfilm.domain.kafka.service;

import com.onfilm.domain.kafka.entity.MediaEncodeOutbox;
import com.onfilm.domain.kafka.entity.MediaEncodeOutboxStatus;
import com.onfilm.domain.kafka.repository.MediaEncodeOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MediaEncodeOutboxTransactionService {
    private static final Duration LEASE = Duration.ofMinutes(2);
    private final MediaEncodeOutboxRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ClaimedOutbox> claim(Instant now, int batchSize) {
        List<MediaEncodeOutbox> outboxes = repository.findClaimable(
                MediaEncodeOutboxStatus.PENDING,
                MediaEncodeOutboxStatus.PUBLISHING,
                now,
                PageRequest.of(0, batchSize)
        );
        outboxes.forEach(outbox -> outbox.claim(now, LEASE));
        return outboxes.stream()
                .map(outbox -> new ClaimedOutbox(outbox.getId(), outbox.getJobId(), outbox.getPayload()))
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(String outboxId, Instant now) {
        repository.findById(outboxId).orElseThrow().published(now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FailedOutbox markFailed(String outboxId, String error, Instant now) {
        MediaEncodeOutbox outbox = repository.findById(outboxId).orElseThrow();
        outbox.failed(error, now);
        return new FailedOutbox(outbox.getStatus(), outbox.getAttempts());
    }

    public record ClaimedOutbox(String id, String jobId, String payload) {
    }

    public record FailedOutbox(MediaEncodeOutboxStatus status, int attempts) {
        public boolean retryScheduled() {
            return status == MediaEncodeOutboxStatus.PENDING;
        }
    }
}
