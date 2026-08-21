package com.onfilm.domain.kafka.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onfilm.domain.kafka.message.MediaEncodeRequestedMessage;
import com.onfilm.domain.kafka.producer.MediaEncodeJobProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(MediaEncodeJobProducer.class)
public class MediaEncodeOutboxPublisher {
    private final MediaEncodeOutboxTransactionService transactionService;
    private final MediaEncodeJobProducer producer;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${media-encode.outbox-poll-delay:1000}")
    public void publishPending() {
        for (MediaEncodeOutboxTransactionService.ClaimedOutbox outbox : transactionService.claim(clock.instant(), 50)) {
            try {
                MediaEncodeRequestedMessage message =
                        objectMapper.readValue(outbox.payload(), MediaEncodeRequestedMessage.class);
                if (message.schemaVersion() != MediaEncodeRequestedMessage.CURRENT_SCHEMA_VERSION) {
                    throw new IllegalArgumentException("unsupported media message schema version");
                }
                producer.send(message).get(30, TimeUnit.SECONDS);
                transactionService.markPublished(outbox.id(), clock.instant());
            } catch (Exception exception) {
                String message = rootMessage(exception);
                transactionService.markFailed(outbox.id(), message, clock.instant());
                log.error("Failed to publish media encode outbox. outboxId={}, jobId={}",
                        outbox.id(), outbox.jobId(), exception);
            }
        }
    }

    private String rootMessage(Exception exception) {
        Throwable cause = exception;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
}
