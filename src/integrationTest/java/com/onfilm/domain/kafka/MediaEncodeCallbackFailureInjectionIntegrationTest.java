package com.onfilm.domain.kafka;

import com.onfilm.domain.common.error.exception.InvalidMediaJobStatusTransitionException;
import com.onfilm.domain.kafka.dto.MediaEncodeCompletionRequest;
import com.onfilm.domain.kafka.entity.MediaEncodeJob;
import com.onfilm.domain.kafka.entity.MediaEncodeJobStatus;
import com.onfilm.domain.kafka.message.EncodeJobPreset;
import com.onfilm.domain.kafka.message.EncodeJobType;
import com.onfilm.domain.kafka.repository.MediaEncodeJobRepository;
import com.onfilm.domain.kafka.service.MediaEncodeJobCompletionTransactionService;
import com.onfilm.domain.movie.entity.AgeRating;
import com.onfilm.domain.movie.entity.Movie;
import com.onfilm.domain.movie.repository.MovieRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
@Import(MediaEncodeJobCompletionTransactionService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MediaEncodeCallbackFailureInjectionIntegrationTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-08-31T00:00:00Z");

    @Autowired
    private MediaEncodeJobCompletionTransactionService completionService;

    @Autowired
    private MediaEncodeJobRepository jobRepository;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        jobRepository.deleteAll();
        movieRepository.deleteAll();
    }

    @Test
    void duplicateDoneCallbackDoesNotDuplicateMovieResultAndLateFailureIsRejected() {
        Movie movie = saveMovie();
        MediaEncodeJob job = saveTrailerJob(movie.getId());
        MediaEncodeCompletionRequest request = completionRequest(job);

        assertThat(completionService.complete(job.getId(), request)).isTrue();
        assertThat(completionService.complete(job.getId(), request)).isFalse();
        assertThat(trailerCount(movie.getId())).isEqualTo(1);

        assertThatThrownBy(() -> inTransaction(() ->
                jobRepository.findById(job.getId()).orElseThrow()
                        .markFailed("LATE_FAILURE", "injected late callback", REQUESTED_AT.plusSeconds(40))))
                .isInstanceOf(InvalidMediaJobStatusTransitionException.class);
        assertThat(jobRepository.findById(job.getId()).orElseThrow().getStatus())
                .isEqualTo(MediaEncodeJobStatus.DONE);
    }

    @Test
    void doneCallbackAfterFailedStateIsRejectedAndMovieResultRemainsUnchanged() {
        Movie movie = saveMovie();
        MediaEncodeJob job = saveTrailerJob(movie.getId());
        inTransaction(() -> jobRepository.findById(job.getId()).orElseThrow()
                .markFailed("ENCODE_FAILED", "injected worker failure", REQUESTED_AT.plusSeconds(20)));

        assertThatThrownBy(() -> completionService.complete(job.getId(), completionRequest(job)))
                .isInstanceOf(InvalidMediaJobStatusTransitionException.class);

        assertThat(jobRepository.findById(job.getId()).orElseThrow().getStatus())
                .isEqualTo(MediaEncodeJobStatus.FAILED);
        assertThat(trailerCount(movie.getId())).isZero();
    }

    private Movie saveMovie() {
        return movieRepository.saveAndFlush(Movie.create(
                "Failure Injection Movie", 120, 2026,
                "movie/1/file/original/index.m3u8", null, AgeRating.ALL
        ));
    }

    private MediaEncodeJob saveTrailerJob(Long movieId) {
        String requestId = UUID.randomUUID().toString();
        return jobRepository.saveAndFlush(MediaEncodeJob.requested(
                UUID.randomUUID().toString(), requestId, movieId, 2L,
                EncodeJobType.TRAILER, EncodeJobPreset.VIDEO_HLS_720P_2500K_AAC_96K,
                "bucket", "movie/" + movieId + "/raw/trailer/" + requestId + ".mp4",
                "bucket", "movie/" + movieId + "/trailer/550e8400-e29b-41d4-a716-446655440000/index.m3u8",
                "video/mp4", "application/vnd.apple.mpegurl", REQUESTED_AT
        ));
    }

    private MediaEncodeCompletionRequest completionRequest(MediaEncodeJob job) {
        return new MediaEncodeCompletionRequest(
                job.getTargetBucket(), job.getTargetKey(), job.getTargetContentType(),
                REQUESTED_AT.plusSeconds(30)
        );
    }

    private int trailerCount(Long movieId) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status ->
                movieRepository.findById(movieId).orElseThrow().getTrailers().size()
        );
    }

    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }
}
