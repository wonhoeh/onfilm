package com.onfilm.domain.kafka;

import com.onfilm.domain.kafka.entity.MediaEncodeJob;
import com.onfilm.domain.kafka.entity.MediaEncodeOutbox;
import com.onfilm.domain.kafka.message.EncodeJobPreset;
import com.onfilm.domain.kafka.message.EncodeJobType;
import com.onfilm.domain.kafka.repository.MediaEncodeJobRepository;
import com.onfilm.domain.kafka.repository.MediaEncodeOutboxRepository;
import com.onfilm.domain.token.entity.RefreshToken;
import com.onfilm.domain.token.repository.RefreshTokenRepository;
import com.onfilm.domain.token.service.RefreshTokenSecurityTransactionService;
import com.onfilm.domain.user.entity.User;
import com.onfilm.domain.user.entity.UserEmail;
import com.onfilm.domain.user.entity.Username;
import com.onfilm.domain.user.repository.UserRepository;
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
@Import(RefreshTokenSecurityTransactionService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MySqlTransactionBoundaryIntegrationTest extends MySqlContainerSupport {

    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");

    @Autowired
    private MediaEncodeJobRepository jobRepository;

    @Autowired
    private MediaEncodeOutboxRepository outboxRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenSecurityTransactionService securityTransactionService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        outboxRepository.deleteAll();
        jobRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void jobAndOutboxCommitOrRollbackAsOneDatabaseResult() {
        MediaEncodeJob committedJob = newJob();
        MediaEncodeOutbox committedOutbox = newOutbox(committedJob);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            jobRepository.save(committedJob);
            outboxRepository.save(committedOutbox);
        });

        assertThat(jobRepository.findById(committedJob.getId())).isPresent();
        assertThat(outboxRepository.findById(committedOutbox.getId())).isPresent();

        MediaEncodeJob rolledBackJob = newJob();
        MediaEncodeOutbox rolledBackOutbox = newOutbox(rolledBackJob);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            jobRepository.save(rolledBackJob);
            outboxRepository.save(rolledBackOutbox);
            throw new IllegalStateException("injected transaction failure");
        })).hasMessage("injected transaction failure");

        assertThat(jobRepository.findById(rolledBackJob.getId())).isEmpty();
        assertThat(outboxRepository.findById(rolledBackOutbox.getId())).isEmpty();
    }

    @Test
    void requiresNewSecurityRecordSurvivesOuterTransactionRollback() {
        User user = userRepository.saveAndFlush(createUser());
        RefreshToken expired = refreshTokenRepository.saveAndFlush(
                RefreshToken.issue(
                        user.getId(),
                        "a".repeat(RefreshToken.TOKEN_HASH_LENGTH),
                        NOW.minusSeconds(7200),
                        NOW.minusSeconds(3600)
                )
        );
        MediaEncodeJob outerJob = newJob();
        TransactionTemplate outerTransaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> outerTransaction.executeWithoutResult(status -> {
            jobRepository.save(outerJob);
            securityTransactionService.recordExpiredUse(expired.getId(), NOW);
            throw new IllegalStateException("outer request returns unauthorized");
        })).hasMessage("outer request returns unauthorized");

        assertThat(jobRepository.findById(outerJob.getId())).isEmpty();
        RefreshToken recorded = refreshTokenRepository.findById(expired.getId()).orElseThrow();
        assertThat(recorded.getRevokedAt()).isEqualTo(NOW);
        assertThat(recorded.getLastUsedAt()).isEqualTo(NOW);
    }

    private static MediaEncodeOutbox newOutbox(MediaEncodeJob job) {
        return MediaEncodeOutbox.pending(
                UUID.randomUUID().toString(),
                job.getId(),
                "{\"schemaVersion\":1}",
                NOW
        );
    }

    private static User createUser() {
        User user = User.create(
                UserEmail.from("transaction-token@example.com"),
                "encoded-password",
                Username.from("transaction-token")
        );
        user.createPerson("transaction-token");
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
}
