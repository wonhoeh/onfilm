package com.onfilm.domain.kafka;

import com.onfilm.domain.kafka.entity.MediaEncodeJob;
import com.onfilm.domain.kafka.entity.MediaEncodeOutbox;
import com.onfilm.domain.kafka.entity.MediaEncodeOutboxStatus;
import com.onfilm.domain.kafka.message.EncodeJobPreset;
import com.onfilm.domain.kafka.message.EncodeJobType;
import com.onfilm.domain.kafka.repository.MediaEncodeJobRepository;
import com.onfilm.domain.kafka.repository.MediaEncodeOutboxRepository;
import com.onfilm.domain.kafka.service.MediaEncodeOutboxTransactionService;
import com.onfilm.support.MySqlContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.sql.init.mode=never")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MediaEncodeOutboxTransactionService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MediaEncodeOutboxFailureInjectionIntegrationTest extends MySqlContainerSupport {

    private static final Instant NOW = Instant.parse("2026-08-31T00:00:00Z");

    @Autowired
    private MediaEncodeJobRepository jobRepository;

    @Autowired
    private MediaEncodeOutboxRepository outboxRepository;

    @Autowired
    private MediaEncodeOutboxTransactionService transactions;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        outboxRepository.deleteAll();
        jobRepository.deleteAll();
    }

    @Test
    void publisherCrashAfterClaimIsRecoveredOnlyAfterLeaseExpires() {
        MediaEncodeOutbox outbox = saveOutbox();

        assertThat(transactions.claim(NOW, 1))
                .extracting(MediaEncodeOutboxTransactionService.ClaimedOutbox::id)
                .containsExactly(outbox.getId());
        assertThat(transactions.claim(NOW.plusSeconds(119), 1)).isEmpty();

        assertThat(transactions.claim(NOW.plusSeconds(121), 1))
                .extracting(MediaEncodeOutboxTransactionService.ClaimedOutbox::id)
                .containsExactly(outbox.getId());

        MediaEncodeOutbox recovered = outboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(MediaEncodeOutboxStatus.PUBLISHING);
        assertThat(recovered.getAttempts()).isEqualTo(2);
    }

    @Test
    void repeatedPublishFailuresEndInTraceableDeadState() {
        MediaEncodeOutbox outbox = saveOutbox();
        Instant attemptAt = NOW;

        for (int attempt = 1; attempt <= MediaEncodeOutbox.MAX_ATTEMPTS; attempt++) {
            assertThat(transactions.claim(attemptAt, 1)).hasSize(1);
            transactions.markFailed(outbox.getId(), "injected kafka timeout", attemptAt);

            MediaEncodeOutbox failed = outboxRepository.findById(outbox.getId()).orElseThrow();
            assertThat(failed.getAttempts()).isEqualTo(attempt);
            if (attempt < MediaEncodeOutbox.MAX_ATTEMPTS) {
                assertThat(failed.getStatus()).isEqualTo(MediaEncodeOutboxStatus.PENDING);
                assertThat(failed.getNextAttemptAt()).isAfter(attemptAt);
                attemptAt = failed.getNextAttemptAt();
            }
        }

        MediaEncodeOutbox dead = outboxRepository.findById(outbox.getId()).orElseThrow();
        assertThat(dead.getStatus()).isEqualTo(MediaEncodeOutboxStatus.DEAD);
        assertThat(dead.getLastError()).isEqualTo("injected kafka timeout");
        assertThat(transactions.claim(attemptAt.plusSeconds(1), 1)).isEmpty();
    }

    @Test
    void databaseFailureRollsBackJobAndOutboxTogether() {
        MediaEncodeJob job = newJob();
        MediaEncodeOutbox outbox = MediaEncodeOutbox.pending(
                UUID.randomUUID().toString(), job.getId(), "{\"schemaVersion\":1}", NOW
        );
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            jobRepository.save(job);
            outboxRepository.save(outbox);
            throw new IllegalStateException("injected database failure");
        })).hasMessage("injected database failure");

        assertThat(jobRepository.findById(job.getId())).isEmpty();
        assertThat(outboxRepository.findById(outbox.getId())).isEmpty();
    }

    private MediaEncodeOutbox saveOutbox() {
        MediaEncodeOutbox outbox = MediaEncodeOutbox.pending(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                "{\"schemaVersion\":1}", NOW
        );
        return outboxRepository.saveAndFlush(outbox);
    }

    private MediaEncodeJob newJob() {
        String requestId = UUID.randomUUID().toString();
        return MediaEncodeJob.requested(
                UUID.randomUUID().toString(), requestId, 1L, 2L,
                EncodeJobType.MOVIE, EncodeJobPreset.VIDEO_HLS_720P_2500K_AAC_96K,
                "bucket", "movie/1/raw/file/" + requestId + ".mp4",
                "bucket", "movie/1/file/550e8400-e29b-41d4-a716-446655440000/index.m3u8",
                "video/mp4", "application/vnd.apple.mpegurl", NOW
        );
    }
}
