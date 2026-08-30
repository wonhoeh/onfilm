package com.onfilm.domain.kafka.metrics;

import com.onfilm.domain.kafka.entity.MediaEncodeJobStatus;
import com.onfilm.domain.kafka.entity.MediaEncodeOutboxStatus;
import com.onfilm.domain.kafka.repository.MediaEncodeJobRepository;
import com.onfilm.domain.kafka.repository.MediaEncodeOutboxRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class MediaEncodeMetricSnapshotServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");

    @Mock MediaEncodeOutboxRepository outboxRepository;
    @Mock MediaEncodeJobRepository jobRepository;

    private SimpleMeterRegistry registry;
    private MediaEncodeMetricSnapshotService service;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        MediaEncodeMetrics metrics = new MediaEncodeMetrics(registry);
        service = new MediaEncodeMetricSnapshotService(
                outboxRepository,
                jobRepository,
                metrics,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void refreshesOutboxAndJobStateGaugesFromDatabaseSnapshot() {
        given(outboxRepository.countByStatus(any())).willAnswer(invocation -> switch (
                invocation.<MediaEncodeOutboxStatus>getArgument(0)
        ) {
            case PENDING -> 3L;
            case DEAD -> 1L;
            default -> 0L;
        });
        given(outboxRepository.findOldestCreatedAtByStatus(MediaEncodeOutboxStatus.PENDING))
                .willReturn(Optional.of(NOW.minusSeconds(125)));
        given(jobRepository.countByStatus(any())).willAnswer(invocation -> switch (
                invocation.<MediaEncodeJobStatus>getArgument(0)
        ) {
            case REQUESTED -> 2L;
            case PROCESSING -> 4L;
            case DONE -> 10L;
            case FAILED -> 1L;
        });

        service.refresh();

        assertThat(gauge("media.encode.outbox.records", "status", "pending")).isEqualTo(3);
        assertThat(gauge("media.encode.outbox.records", "status", "dead")).isEqualTo(1);
        assertThat(gauge("media.encode.outbox.oldest.pending.age", null, null)).isEqualTo(125);
        assertThat(gauge("media.encode.job.records", "status", "requested")).isEqualTo(2);
        assertThat(gauge("media.encode.job.records", "status", "processing")).isEqualTo(4);
        assertThat(gauge("media.encode.job.records", "status", "done")).isEqualTo(10);
        assertThat(gauge("media.encode.job.records", "status", "failed")).isEqualTo(1);
    }

    private double gauge(String name, String tagKey, String tagValue) {
        var search = registry.get(name);
        if (tagKey != null) {
            search = search.tag(tagKey, tagValue);
        }
        return search.gauge().value();
    }
}
