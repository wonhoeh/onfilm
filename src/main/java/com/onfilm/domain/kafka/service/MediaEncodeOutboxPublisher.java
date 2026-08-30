package com.onfilm.domain.kafka.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onfilm.domain.common.logging.CorrelationIdContext;
import com.onfilm.domain.kafka.metrics.MediaEncodeMetrics;
import com.onfilm.domain.kafka.message.MediaEncodeRequestedMessage;
import com.onfilm.domain.kafka.producer.MediaEncodeJobProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.concurrent.TimeUnit;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(MediaEncodeJobProducer.class)
public class MediaEncodeOutboxPublisher {
    private final MediaEncodeOutboxTransactionService transactionService;
    private final MediaEncodeJobProducer producer;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final MediaEncodeMetrics metrics;

    @Scheduled(fixedDelayString = "${media-encode.outbox-poll-delay:1000}")
    public void publishPending() {
        for (MediaEncodeOutboxTransactionService.ClaimedOutbox outbox : transactionService.claim(clock.instant(), 50)) {
            try (MDC.MDCCloseable ignoredJob = MDC.putCloseable("jobId", outbox.jobId())) {
                publish(outbox);
            }
        }
    }

    private void publish(MediaEncodeOutboxTransactionService.ClaimedOutbox outbox) {
        MediaEncodeRequestedMessage message;
        try {
            message = objectMapper.readValue(outbox.payload(), MediaEncodeRequestedMessage.class);
        } catch (Exception exception) {
            markFailed(outbox, exception);
            return;
        }

        String correlationId = CorrelationIdContext.resolve(
                message.correlationId(),
                message.requestId()
        );
        try (MDC.MDCCloseable ignoredCorrelation = MDC.putCloseable(
                CorrelationIdContext.MDC_KEY,
                correlationId
        ); MDC.MDCCloseable ignoredRequest = MDC.putCloseable("requestId", message.requestId())) {
            try {
                if (message.schemaVersion() != MediaEncodeRequestedMessage.CURRENT_SCHEMA_VERSION) {
                    throw new IllegalArgumentException("unsupported media message schema version");
                }
                producer.send(message).get(30, TimeUnit.SECONDS);
                transactionService.markPublished(outbox.id(), clock.instant());
                metrics.recordOutboxPublished();
                log.info("Media encode outbox published. {} {} {}",
                        kv("eventType", "MEDIA_ENCODE_OUTBOX_PUBLISHED"),
                        kv("outboxId", outbox.id()),
                        kv("status", "PUBLISHED"));
            } catch (Exception exception) {
                markFailed(outbox, exception);
            }
        }
    }

    private void markFailed(
            MediaEncodeOutboxTransactionService.ClaimedOutbox outbox,
            Exception exception
    ) {
        String message = rootMessage(exception);
        MediaEncodeOutboxTransactionService.FailedOutbox failed =
                transactionService.markFailed(outbox.id(), message, clock.instant());
        metrics.recordOutboxPublishFailed(failed.retryScheduled());
        String eventType = failed.retryScheduled()
                ? "MEDIA_ENCODE_OUTBOX_PUBLISH_FAILED"
                : "MEDIA_ENCODE_OUTBOX_DEAD";
        log.error("Media encode outbox publish failed. {} {} {} {} {} {}",
                kv("eventType", eventType),
                kv("outboxId", outbox.id()),
                kv("jobId", outbox.jobId()),
                kv("status", failed.status()),
                kv("attempt", failed.attempts()),
                kv("retryable", failed.retryScheduled()),
                exception);
    }

    private String rootMessage(Exception exception) {
        Throwable cause = exception;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
}
