package com.onfilm.domain.kafka.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onfilm.domain.file.service.StorageKeyPolicy;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.kafka.entity.MediaUploadRequest;
import com.onfilm.domain.kafka.message.EncodeJobType;
import com.onfilm.domain.kafka.repository.MediaEncodeJobRepository;
import com.onfilm.domain.kafka.repository.MediaEncodeOutboxRepository;
import com.onfilm.domain.kafka.repository.MediaUploadRequestRepository;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig(MediaEncodeJobTransactionBoundaryTest.TestConfig.class)
class MediaEncodeJobTransactionBoundaryTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private MediaEncodeJobCommandService service;

    @Autowired
    private MediaUploadRequestRepository uploadRequestRepository;

    @Autowired
    private StorageService storageService;

    @Test
    void checksStorageOutsideTransactionAndLocksOnlyForDatabaseMutation() {
        String requestId = UUID.randomUUID().toString();
        String sourceKey = "movie/1/raw/file/" + requestId + ".mp4";
        MediaUploadRequest upload = MediaUploadRequest.issue(
                requestId, 2L, 1L, EncodeJobType.MOVIE,
                "bucket", sourceKey, "video/mp4",
                NOW.minusSeconds(10), NOW.plusSeconds(600)
        );
        given(uploadRequestRepository.findById(requestId)).willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return Optional.of(upload);
        });
        given(storageService.exists(sourceKey)).willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return true;
        });
        given(uploadRequestRepository.findByIdForUpdate(requestId)).willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return Optional.of(upload);
        });

        String jobId = service.requestMovieEncoding(
                requestId, 1L, 2L, "bucket", sourceKey, "bucket",
                "movie/1/file/550e8400-e29b-41d4-a716-446655440000/index.m3u8",
                "video/mp4"
        );

        assertThat(jobId).isEqualTo(upload.getJobId());
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        MediaEncodeJobRepository jobRepository() {
            return mock(MediaEncodeJobRepository.class);
        }

        @Bean
        MediaUploadRequestRepository uploadRequestRepository() {
            return mock(MediaUploadRequestRepository.class);
        }

        @Bean
        MediaEncodeOutboxRepository outboxRepository() {
            return mock(MediaEncodeOutboxRepository.class);
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
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        MediaEncodeJobTransactionService transactionService(
                MediaEncodeJobRepository jobRepository,
                MediaUploadRequestRepository uploadRequestRepository,
                MediaEncodeOutboxRepository outboxRepository,
                ObjectMapper objectMapper
        ) {
            return new MediaEncodeJobTransactionService(
                    jobRepository, uploadRequestRepository,
                    outboxRepository, objectMapper
            );
        }

        @Bean
        MediaEncodeJobCommandService commandService(
                MediaEncodeJobTransactionService transactionService,
                StorageService storageService,
                StorageKeyPolicy storageKeyPolicy,
                Clock clock,
                com.onfilm.domain.kafka.metrics.MediaEncodeMetrics metrics
        ) {
            return new MediaEncodeJobCommandService(
                    transactionService, storageService, storageKeyPolicy, clock, metrics
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
                    "jdbc:h2:mem:media-job-boundary-test;DB_CLOSE_DELAY=-1",
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
