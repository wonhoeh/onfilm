package com.onfilm.domain.kafka.metrics;

import com.onfilm.domain.kafka.entity.MediaEncodeJobStatus;
import com.onfilm.domain.kafka.entity.MediaEncodeOutboxStatus;
import com.onfilm.domain.kafka.message.EncodeJobType;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class MediaEncodeMetrics {
    private final MeterRegistry registry;
    private final Map<MediaEncodeOutboxStatus, AtomicLong> outboxCounts =
            new EnumMap<>(MediaEncodeOutboxStatus.class);
    private final Map<MediaEncodeJobStatus, AtomicLong> jobCounts =
            new EnumMap<>(MediaEncodeJobStatus.class);
    private final AtomicLong oldestPendingOutboxAgeSeconds = new AtomicLong();

    public MediaEncodeMetrics(MeterRegistry registry) {
        this.registry = registry;
        registerOutboxGauges();
        registerJobGauges();
    }

    public void recordOutboxPublished() {
        registry.counter("media.encode.outbox.publish", "result", "success").increment();
    }

    public void recordOutboxPublishFailed(boolean retryScheduled) {
        registry.counter("media.encode.outbox.publish", "result", "failure").increment();
        registry.counter(
                "media.encode.outbox.failure",
                "result",
                retryScheduled ? "retry_scheduled" : "dead"
        ).increment();
    }

    public void recordCallback(String callbackType, String result, long elapsedNanos) {
        registry.counter(
                "media.encode.callback",
                "type", callbackType,
                "result", result
        ).increment();
        Timer.builder("media.encode.callback.duration")
                .tag("type", callbackType)
                .tag("result", result)
                .register(registry)
                .record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    public void recordJobCreated(EncodeJobType jobType) {
        registry.counter(
                "media.encode.job.created",
                "type", jobType.name().toLowerCase()
        ).increment();
    }

    public void recordJobTransition(EncodeJobType jobType, MediaEncodeJobStatus status) {
        registry.counter(
                "media.encode.job.transition",
                "type", jobType.name().toLowerCase(),
                "status", status.name().toLowerCase()
        ).increment();
    }

    public void recordJobTerminal(EncodeJobType jobType, String result, Duration duration) {
        registry.counter(
                "media.encode.job.terminal",
                "type", jobType.name().toLowerCase(),
                "result", result
        ).increment();
        Timer.builder("media.encode.job.duration")
                .tag("type", jobType.name().toLowerCase())
                .tag("result", result)
                .register(registry)
                .record(nonNegative(duration));
    }

    public void recordJobTimeout(EncodeJobType jobType, Duration duration) {
        registry.counter(
                "media.encode.job.timeout",
                "type", jobType.name().toLowerCase()
        ).increment();
        recordJobTransition(jobType, MediaEncodeJobStatus.FAILED);
        recordJobTerminal(jobType, "timeout", duration);
    }

    public void updateOutboxCount(MediaEncodeOutboxStatus status, long count) {
        outboxCounts.get(status).set(Math.max(0, count));
    }

    public void updateJobCount(MediaEncodeJobStatus status, long count) {
        jobCounts.get(status).set(Math.max(0, count));
    }

    public void updateOldestPendingOutboxAge(Duration age) {
        oldestPendingOutboxAgeSeconds.set(nonNegative(age).toSeconds());
    }

    private void registerOutboxGauges() {
        for (MediaEncodeOutboxStatus status : MediaEncodeOutboxStatus.values()) {
            AtomicLong value = new AtomicLong();
            outboxCounts.put(status, value);
            Gauge.builder("media.encode.outbox.records", value, AtomicLong::get)
                    .tag("status", status.name().toLowerCase())
                    .register(registry);
        }
        Gauge.builder(
                        "media.encode.outbox.oldest.pending.age",
                        oldestPendingOutboxAgeSeconds,
                        AtomicLong::get
                )
                .baseUnit("seconds")
                .register(registry);
    }

    private void registerJobGauges() {
        for (MediaEncodeJobStatus status : MediaEncodeJobStatus.values()) {
            AtomicLong value = new AtomicLong();
            jobCounts.put(status, value);
            Gauge.builder("media.encode.job.records", value, AtomicLong::get)
                    .tag("status", status.name().toLowerCase())
                    .register(registry);
        }
    }

    private Duration nonNegative(Duration duration) {
        if (duration == null || duration.isNegative()) {
            return Duration.ZERO;
        }
        return duration;
    }
}
