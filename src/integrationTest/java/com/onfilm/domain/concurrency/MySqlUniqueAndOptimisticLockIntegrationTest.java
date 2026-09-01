package com.onfilm.domain.concurrency;

import com.onfilm.domain.kafka.entity.MediaEncodeJob;
import com.onfilm.domain.kafka.entity.MediaEncodeJobStatus;
import com.onfilm.domain.kafka.message.EncodeJobPreset;
import com.onfilm.domain.kafka.message.EncodeJobType;
import com.onfilm.domain.kafka.repository.MediaEncodeJobRepository;
import com.onfilm.domain.token.entity.RefreshToken;
import com.onfilm.domain.token.repository.RefreshTokenRepository;
import com.onfilm.domain.user.entity.User;
import com.onfilm.domain.user.entity.UserEmail;
import com.onfilm.domain.user.entity.Username;
import com.onfilm.domain.user.repository.UserRepository;
import com.onfilm.support.MySqlContainerSupport;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.sql.init.mode=never")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MySqlUniqueAndOptimisticLockIntegrationTest extends MySqlContainerSupport {

    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");
    private static final long WAIT_TIMEOUT_SECONDS = 10;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private MediaEncodeJobRepository jobRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        jobRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void uniqueConstraintAllowsOnlyOneConcurrentRegistrationForSameEmail()
            throws Exception {
        String email = "race@example.com";
        CyclicBarrier afterAvailabilityCheck = new CyclicBarrier(2);

        List<Attempt> attempts = runConcurrently(index -> newTransaction()
                .executeWithoutResult(status -> {
                    assertThat(userRepository.existsByEmail(email)).isFalse();
                    assertThat(userRepository.existsByUsernameNormalized("race-user-" + index))
                            .isFalse();
                    await(afterAvailabilityCheck);
                    userRepository.saveAndFlush(createUser(
                            email,
                            "race-user-" + index
                    ));
                }));

        assertSingleUniqueWinner(attempts, "uk_users_email");
        assertThat(userRepository.count()).isOne();
    }

    @Test
    void uniqueConstraintAllowsOnlyOneConcurrentRegistrationForSameUsername()
            throws Exception {
        String username = "same-race-user";
        CyclicBarrier afterAvailabilityCheck = new CyclicBarrier(2);

        List<Attempt> attempts = runConcurrently(index -> newTransaction()
                .executeWithoutResult(status -> {
                    assertThat(userRepository.existsByEmail("race-" + index + "@example.com"))
                            .isFalse();
                    assertThat(userRepository.existsByUsernameNormalized(username)).isFalse();
                    await(afterAvailabilityCheck);
                    userRepository.saveAndFlush(createUser(
                            "race-" + index + "@example.com",
                            username
                    ));
                }));

        assertSingleUniqueWinner(attempts, "uk_users_username_normalized");
        assertThat(userRepository.count()).isOne();
    }

    @Test
    void refreshTokenVersionRejectsTheSecondConcurrentConsumption() throws Exception {
        User user = userRepository.saveAndFlush(
                createUser("token-race@example.com", "token-race-user")
        );
        RefreshToken token = refreshTokenRepository.saveAndFlush(
                RefreshToken.issue(
                        user.getId(),
                        "r".repeat(RefreshToken.TOKEN_HASH_LENGTH),
                        NOW.minusSeconds(60),
                        NOW.plusSeconds(3600)
                )
        );
        CyclicBarrier afterVersionRead = new CyclicBarrier(2);

        List<Attempt> attempts = runConcurrently(index -> newTransaction()
                .executeWithoutResult(status -> {
                    RefreshToken loaded = refreshTokenRepository.findById(token.getId())
                            .orElseThrow();
                    assertThat(loaded.getVersion()).isZero();
                    await(afterVersionRead);
                    loaded.consume(NOW);
                    refreshTokenRepository.saveAndFlush(loaded);
                }));

        assertSingleOptimisticLockWinner(attempts);
        RefreshToken reloaded = refreshTokenRepository.findById(token.getId()).orElseThrow();
        assertThat(reloaded.getVersion()).isOne();
        assertThat(reloaded.getRevokedAt()).isEqualTo(NOW);
        assertThat(reloaded.getLastUsedAt()).isEqualTo(NOW);
    }

    @Test
    void mediaJobVersionAllowsOnlyOneConcurrentTerminalTransition() throws Exception {
        MediaEncodeJob job = jobRepository.saveAndFlush(newJob());
        CyclicBarrier afterVersionRead = new CyclicBarrier(2);

        List<Attempt> attempts = runConcurrently(index -> newTransaction()
                .executeWithoutResult(status -> {
                    MediaEncodeJob loaded = jobRepository.findById(job.getId()).orElseThrow();
                    assertThat(loaded.getVersion()).isZero();
                    await(afterVersionRead);
                    if (index == 0) {
                        loaded.markDone(NOW.plusSeconds(10));
                    } else {
                        loaded.markFailed(
                                "INJECTED_FAILURE",
                                "concurrent terminal transition",
                                NOW.plusSeconds(10)
                        );
                    }
                    jobRepository.saveAndFlush(loaded);
                }));

        assertSingleOptimisticLockWinner(attempts);
        MediaEncodeJob reloaded = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(reloaded.getVersion()).isOne();
        assertThat(reloaded.getStatus())
                .isIn(MediaEncodeJobStatus.DONE, MediaEncodeJobStatus.FAILED);
        assertThat(reloaded.getCompletedAt()).isEqualTo(NOW.plusSeconds(10));
        if (reloaded.getStatus() == MediaEncodeJobStatus.DONE) {
            assertThat(reloaded.getFailureCode()).isNull();
            assertThat(reloaded.getFailureReason()).isNull();
        } else {
            assertThat(reloaded.getFailureCode()).isEqualTo("INJECTED_FAILURE");
            assertThat(reloaded.getFailureReason())
                    .isEqualTo("concurrent terminal transition");
        }
    }

    private List<Attempt> runConcurrently(IntConsumer operation) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Attempt> first = executor.submit(() -> attempt(() -> operation.accept(0)));
            Future<Attempt> second = executor.submit(() -> attempt(() -> operation.accept(1)));
            return List.of(
                    first.get(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    second.get(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            );
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .isTrue();
        }
    }

    private TransactionTemplate newTransaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
        return transaction;
    }

    private static Attempt attempt(Runnable runnable) {
        try {
            runnable.run();
            return Attempt.success();
        } catch (RuntimeException exception) {
            return Attempt.failure(exception);
        }
    }

    private static void assertSingleUniqueWinner(
            List<Attempt> attempts,
            String expectedConstraint
    ) {
        assertThat(attempts).filteredOn(Attempt::succeeded).hasSize(1);
        Attempt failed = attempts.stream().filter(attempt -> !attempt.succeeded())
                .findFirst()
                .orElseThrow();
        assertThat(failed.failure()).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(findConstraintName(failed.failure()))
                .containsIgnoringCase(expectedConstraint);
    }

    private static void assertSingleOptimisticLockWinner(List<Attempt> attempts) {
        assertThat(attempts).filteredOn(Attempt::succeeded).hasSize(1);
        assertThat(attempts)
                .filteredOn(attempt -> !attempt.succeeded())
                .singleElement()
                .extracting(Attempt::failure)
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    private static String findConstraintName(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
            current = current.getCause();
        }
        return null;
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("concurrency barrier failed", exception);
        }
    }

    private static User createUser(String email, String username) {
        User user = User.create(
                UserEmail.from(email),
                "encoded-password",
                Username.from(username)
        );
        user.createPerson(username);
        return user;
    }

    private static MediaEncodeJob newJob() {
        String requestId = UUID.randomUUID().toString();
        return MediaEncodeJob.requested(
                UUID.randomUUID().toString(),
                requestId,
                1L,
                2L,
                EncodeJobType.MOVIE,
                EncodeJobPreset.VIDEO_HLS_720P_2500K_AAC_96K,
                "bucket",
                "movie/1/raw/file/" + requestId + ".mp4",
                "bucket",
                "movie/1/file/550e8400-e29b-41d4-a716-446655440000/index.m3u8",
                "video/mp4",
                "application/vnd.apple.mpegurl",
                NOW
        );
    }

    private record Attempt(boolean succeeded, RuntimeException failure) {
        private static Attempt success() {
            return new Attempt(true, null);
        }

        private static Attempt failure(RuntimeException exception) {
            return new Attempt(false, exception);
        }
    }
}
