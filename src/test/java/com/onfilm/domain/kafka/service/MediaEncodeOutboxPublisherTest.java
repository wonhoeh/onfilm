package com.onfilm.domain.kafka.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onfilm.domain.kafka.entity.MediaEncodeOutbox;
import com.onfilm.domain.kafka.entity.MediaEncodeOutboxStatus;
import com.onfilm.domain.kafka.message.*;
import com.onfilm.domain.kafka.metrics.MediaEncodeMetrics;
import com.onfilm.domain.kafka.producer.MediaEncodeJobProducer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaEncodeOutboxPublisherTest {
    @Mock MediaEncodeOutboxTransactionService transactionService;
    @Mock MediaEncodeJobProducer producer;
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private MediaEncodeOutboxPublisher publisher;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        publisher = new MediaEncodeOutboxPublisher(
                transactionService, producer, new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(now, ZoneOffset.UTC), new MediaEncodeMetrics(meterRegistry));
    }

    @Test
    void marksPublishedOnlyAfterKafkaAcknowledgement() throws Exception {
        MediaEncodeRequestedMessage message = message();
        String payload = new ObjectMapper().findAndRegisterModules().writeValueAsString(message);
        given(transactionService.claim(now, 50)).willReturn(List.of(
                new MediaEncodeOutboxTransactionService.ClaimedOutbox("outbox", message.jobId(), payload)));
        given(producer.send(message)).willAnswer(invocation -> {
            assertThat(MDC.get("correlationId")).isEqualTo(message.correlationId());
            assertThat(MDC.get("requestId")).isEqualTo(message.requestId());
            assertThat(MDC.get("jobId")).isEqualTo(message.jobId());
            return CompletableFuture.completedFuture(null);
        });

        publisher.publishPending();

        verify(transactionService).markPublished("outbox", now);
        verify(transactionService, never()).markFailed(any(), any(), any());
        assertThat(meterRegistry.counter(
                "media.encode.outbox.publish", "result", "success").count()).isEqualTo(1);
    }

    @Test
    void recordsFailureForRetryWhenKafkaFails() throws Exception {
        MediaEncodeRequestedMessage message = message();
        String payload = new ObjectMapper().findAndRegisterModules().writeValueAsString(message);
        given(transactionService.claim(now, 50)).willReturn(List.of(
                new MediaEncodeOutboxTransactionService.ClaimedOutbox("outbox", message.jobId(), payload)));
        given(producer.send(message)).willReturn(
                CompletableFuture.failedFuture(new IllegalStateException("kafka unavailable")));
        given(transactionService.markFailed(eq("outbox"), any(), eq(now))).willReturn(
                new MediaEncodeOutboxTransactionService.FailedOutbox(
                        MediaEncodeOutboxStatus.PENDING, 1));

        publisher.publishPending();

        verify(transactionService).markFailed(eq("outbox"), contains("kafka unavailable"), eq(now));
        verify(transactionService, never()).markPublished(any(), any());
        assertThat(meterRegistry.counter(
                "media.encode.outbox.publish", "result", "failure").count()).isEqualTo(1);
        assertThat(meterRegistry.counter(
                "media.encode.outbox.failure", "result", "retry_scheduled").count()).isEqualTo(1);
    }

    @Test
    void recordsDeadMetricWhenPublishAttemptsAreExhausted() throws Exception {
        MediaEncodeRequestedMessage message = message();
        String payload = new ObjectMapper().findAndRegisterModules().writeValueAsString(message);
        given(transactionService.claim(now, 50)).willReturn(List.of(
                new MediaEncodeOutboxTransactionService.ClaimedOutbox(
                        "outbox", message.jobId(), payload)));
        given(producer.send(message)).willReturn(
                CompletableFuture.failedFuture(new IllegalStateException("kafka unavailable")));
        given(transactionService.markFailed(eq("outbox"), any(), eq(now))).willReturn(
                new MediaEncodeOutboxTransactionService.FailedOutbox(
                        MediaEncodeOutboxStatus.DEAD,
                        MediaEncodeOutbox.MAX_ATTEMPTS
                ));

        publisher.publishPending();

        assertThat(meterRegistry.counter(
                "media.encode.outbox.failure", "result", "dead").count()).isEqualTo(1);
    }

    private MediaEncodeRequestedMessage message() {
        String requestId = UUID.randomUUID().toString();
        return new MediaEncodeRequestedMessage(
                1, UUID.randomUUID().toString(), requestId, "corr-123", 1L, 2L,
                EncodeJobType.MOVIE, EncodeJobPreset.VIDEO_HLS_720P_2500K_AAC_96K,
                "bucket", "movie/1/raw/file/" + requestId + ".mp4",
                "bucket", "movie/1/file/id/index.m3u8",
                "video/mp4", "application/vnd.apple.mpegurl", now);
    }
}
