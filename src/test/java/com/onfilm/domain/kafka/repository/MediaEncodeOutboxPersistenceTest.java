package com.onfilm.domain.kafka.repository;

import com.onfilm.domain.kafka.entity.*;
import com.onfilm.domain.kafka.message.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
class MediaEncodeOutboxPersistenceTest {
    @Autowired MediaEncodeJobRepository jobRepository;
    @Autowired MediaEncodeOutboxRepository outboxRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void jobAndOutboxRollbackTogether() {
        MediaEncodeJob job = job();
        MediaEncodeOutbox outbox = MediaEncodeOutbox.pending(
                UUID.randomUUID().toString(), job.getId(), "{\"schemaVersion\":1}", job.getRequestedAt());
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            jobRepository.save(job);
            outboxRepository.save(outbox);
            throw new IllegalStateException("force rollback");
        })).hasMessage("force rollback");

        assertThat(jobRepository.findById(job.getId())).isEmpty();
        assertThat(outboxRepository.findById(outbox.getId())).isEmpty();
    }

    @Test
    void persistsJobAndOutboxAndFindsThemByStableIdentifiers() {
        MediaEncodeJob job = job();
        MediaEncodeOutbox outbox = MediaEncodeOutbox.pending(
                UUID.randomUUID().toString(), job.getId(), "{\"schemaVersion\":1}", job.getRequestedAt());
        jobRepository.saveAndFlush(job);
        outboxRepository.saveAndFlush(outbox);

        assertThat(jobRepository.findByRequestId(job.getRequestId())).contains(job);
        assertThat(outboxRepository.findByJobId(job.getId())).contains(outbox);
        assertThat(job.getVersion()).isNotNull();
        assertThat(outbox.getVersion()).isNotNull();
    }

    private MediaEncodeJob job() {
        String requestId = UUID.randomUUID().toString();
        return MediaEncodeJob.requested(
                UUID.randomUUID().toString(), requestId, 1L, 2L,
                EncodeJobType.MOVIE, EncodeJobPreset.VIDEO_HLS_720P_2500K_AAC_96K,
                "bucket", "movie/1/raw/file/" + requestId + ".mp4",
                "bucket", "movie/1/file/550e8400-e29b-41d4-a716-446655440000/index.m3u8",
                "video/mp4", "application/vnd.apple.mpegurl",
                Instant.parse("2026-01-01T00:00:00Z"));
    }
}
