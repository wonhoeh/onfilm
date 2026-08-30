package com.onfilm.domain.kafka.service;

import com.onfilm.domain.file.service.StorageKeyPolicy;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.kafka.dto.MediaEncodeCompletionRequest;
import com.onfilm.domain.kafka.entity.MediaEncodeJob;
import com.onfilm.domain.kafka.entity.MediaEncodeJobStatus;
import com.onfilm.domain.kafka.message.EncodeJobPreset;
import com.onfilm.domain.kafka.message.EncodeJobType;
import com.onfilm.domain.kafka.repository.MediaEncodeJobRepository;
import com.onfilm.domain.movie.entity.AgeRating;
import com.onfilm.domain.movie.entity.Movie;
import com.onfilm.domain.movie.repository.MovieRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig(MediaEncodeJobCompletionTransactionBoundaryTest.TestConfig.class)
class MediaEncodeJobCompletionTransactionBoundaryTest {

    @Autowired
    private MediaEncodeJobInternalService service;

    @Autowired
    private MediaEncodeJobRepository jobRepository;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private StorageService storageService;

    @Test
    void checksOutputOutsideTransactionAndAppliesResultInsideTransaction() {
        Instant requestedAt = Instant.parse("2026-01-01T00:00:00Z");
        String requestId = UUID.randomUUID().toString();
        String targetKey =
                "movie/1/trailer/550e8400-e29b-41d4-a716-446655440000/index.m3u8";
        MediaEncodeJob job = MediaEncodeJob.requested(
                UUID.randomUUID().toString(), requestId, 1L, 2L,
                EncodeJobType.TRAILER,
                EncodeJobPreset.VIDEO_HLS_720P_2500K_AAC_96K,
                "bucket", "movie/1/raw/trailer/" + requestId + ".mp4",
                "bucket", targetKey, "video/mp4",
                "application/vnd.apple.mpegurl", requestedAt
        );
        Movie movie = Movie.create(
                "Test Movie", 120, 2020, "movie-key", null, AgeRating.ALL
        );
        given(jobRepository.findById(job.getId())).willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return Optional.of(job);
        });
        given(storageService.exists(targetKey)).willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return true;
        });
        given(movieRepository.findById(1L)).willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return Optional.of(movie);
        });

        service.complete(job.getId(), new MediaEncodeCompletionRequest(
                "bucket", targetKey, "application/vnd.apple.mpegurl",
                requestedAt.plusSeconds(30)
        ));

        assertThat(job.getStatus()).isEqualTo(MediaEncodeJobStatus.DONE);
        assertThat(movie.getTrailers())
                .extracting(trailer -> trailer.getStorageKey())
                .containsExactly(targetKey);
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        MediaEncodeJobRepository jobRepository() {
            return mock(MediaEncodeJobRepository.class);
        }

        @Bean
        MovieRepository movieRepository() {
            return mock(MovieRepository.class);
        }

        @Bean
        StorageService storageService() {
            return mock(StorageService.class);
        }

        @Bean
        StorageKeyPolicy storageKeyPolicy() {
            return mock(StorageKeyPolicy.class);
        }

        @Bean
        MediaEncodeJobCompletionTransactionService completionTransactionService(
                MediaEncodeJobRepository jobRepository,
                MovieRepository movieRepository
        ) {
            return new MediaEncodeJobCompletionTransactionService(
                    jobRepository,
                    movieRepository
            );
        }

        @Bean
        MediaEncodeJobInternalService internalService(
                MediaEncodeJobRepository jobRepository,
                StorageKeyPolicy storageKeyPolicy,
                StorageService storageService,
                MediaEncodeJobCompletionTransactionService completionTransactionService,
                com.onfilm.domain.kafka.metrics.MediaEncodeMetrics metrics
        ) {
            return new MediaEncodeJobInternalService(
                    jobRepository,
                    storageKeyPolicy,
                    storageService,
                    completionTransactionService,
                    metrics
            );
        }

        @Bean
        io.micrometer.core.instrument.MeterRegistry meterRegistry() {
            return new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        }

        @Bean
        com.onfilm.domain.kafka.metrics.MediaEncodeMetrics mediaEncodeMetrics(
                io.micrometer.core.instrument.MeterRegistry registry
        ) {
            return new com.onfilm.domain.kafka.metrics.MediaEncodeMetrics(registry);
        }

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:media-completion-boundary-test;DB_CLOSE_DELAY=-1",
                    "sa",
                    ""
            );
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
