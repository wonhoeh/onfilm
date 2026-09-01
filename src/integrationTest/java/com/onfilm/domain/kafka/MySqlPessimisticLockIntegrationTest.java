package com.onfilm.domain.kafka;

import com.onfilm.domain.kafka.entity.MediaEncodeOutbox;
import com.onfilm.domain.kafka.entity.MediaEncodeOutboxStatus;
import com.onfilm.domain.kafka.entity.MediaUploadRequest;
import com.onfilm.domain.kafka.entity.MediaUploadRequestStatus;
import com.onfilm.domain.kafka.message.EncodeJobType;
import com.onfilm.domain.kafka.repository.MediaEncodeOutboxRepository;
import com.onfilm.domain.kafka.repository.MediaUploadRequestRepository;
import com.onfilm.domain.kafka.service.MediaEncodeOutboxTransactionService;
import com.onfilm.support.MySqlContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.sql.init.mode=never")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MediaEncodeOutboxTransactionService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MySqlPessimisticLockIntegrationTest extends MySqlContainerSupport {

    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");
    private static final long WAIT_TIMEOUT_SECONDS = 10;

    @Autowired
    private MediaUploadRequestRepository uploadRequestRepository;

    @Autowired
    private MediaEncodeOutboxRepository outboxRepository;

    @Autowired
    private MediaEncodeOutboxTransactionService outboxTransactionService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        outboxRepository.deleteAll();
        uploadRequestRepository.deleteAll();
    }

    @Test
    void uploadRequestWriteLockBlocksSecondTransactionUntilCommit() throws Exception {
        MediaUploadRequest upload = uploadRequestRepository.saveAndFlush(newUploadRequest());
        String jobId = UUID.randomUUID().toString();
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);
        CountDownLatch secondTransactionStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> first = executor.submit(() -> newTransaction().executeWithoutResult(status -> {
                MediaUploadRequest locked = uploadRequestRepository
                        .findByIdForUpdate(upload.getId())
                        .orElseThrow();
                firstLocked.countDown();
                await(allowFirstCommit);
                locked.complete(jobId, NOW.plusSeconds(10));
            }));

            assertThat(firstLocked.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            Future<UploadSnapshot> second = executor.submit(() ->
                    newTransaction().execute(status -> {
                        secondTransactionStarted.countDown();
                        MediaUploadRequest locked = uploadRequestRepository
                                .findByIdForUpdate(upload.getId())
                                .orElseThrow();
                        return new UploadSnapshot(locked.getStatus(), locked.getJobId());
                    })
            );

            assertThat(secondTransactionStarted.await(
                    WAIT_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            )).isTrue();
            assertThatThrownBy(() -> second.get(500, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            allowFirstCommit.countDown();
            first.get(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            UploadSnapshot observed = second.get(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(observed.status()).isEqualTo(MediaUploadRequestStatus.COMPLETED);
            assertThat(observed.jobId()).isEqualTo(jobId);
        } finally {
            allowFirstCommit.countDown();
            shutdown(executor);
        }
    }

    @Test
    void outboxWriteLockPreventsTheSameRowFromBeingClaimedTwice() throws Exception {
        MediaEncodeOutbox outbox = outboxRepository.saveAndFlush(newOutbox());
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);
        CountDownLatch secondClaimStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<String> first = executor.submit(() -> newTransaction().execute(status -> {
                MediaEncodeOutbox locked = outboxRepository.findClaimable(
                                MediaEncodeOutboxStatus.PENDING,
                                MediaEncodeOutboxStatus.PUBLISHING,
                                NOW,
                                PageRequest.of(0, 1)
                        )
                        .get(0);
                locked.claim(NOW, Duration.ofMinutes(2));
                firstLocked.countDown();
                await(allowFirstCommit);
                return locked.getId();
            }));

            assertThat(firstLocked.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            Future<List<MediaEncodeOutboxTransactionService.ClaimedOutbox>> second =
                    executor.submit(() -> {
                        secondClaimStarted.countDown();
                        return outboxTransactionService.claim(NOW, 1);
                    });

            assertThat(secondClaimStarted.await(
                    WAIT_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            )).isTrue();
            assertThatThrownBy(() -> second.get(500, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            allowFirstCommit.countDown();
            assertThat(first.get(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .isEqualTo(outbox.getId());
            assertThat(second.get(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEmpty();

            MediaEncodeOutbox reloaded = outboxRepository.findById(outbox.getId())
                    .orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(MediaEncodeOutboxStatus.PUBLISHING);
            assertThat(reloaded.getAttempts()).isEqualTo(1);
            assertThat(reloaded.getLeaseUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(2)));
        } finally {
            allowFirstCommit.countDown();
            shutdown(executor);
        }
    }

    private TransactionTemplate newTransaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
        return transaction;
    }

    private static MediaUploadRequest newUploadRequest() {
        String id = UUID.randomUUID().toString();
        return MediaUploadRequest.issue(
                id,
                2L,
                1L,
                EncodeJobType.MOVIE,
                "bucket",
                "movie/1/raw/file/" + id + ".mp4",
                "video/mp4",
                NOW,
                NOW.plusSeconds(3600)
        );
    }

    private static MediaEncodeOutbox newOutbox() {
        return MediaEncodeOutbox.pending(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "{\"schemaVersion\":1}",
                NOW
        );
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch wait timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("latch wait interrupted", exception);
        }
    }

    private static void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isTrue();
    }

    private record UploadSnapshot(MediaUploadRequestStatus status, String jobId) {
    }
}
