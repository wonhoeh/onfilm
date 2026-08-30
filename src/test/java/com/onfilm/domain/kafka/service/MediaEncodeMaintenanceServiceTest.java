package com.onfilm.domain.kafka.service;

import com.onfilm.domain.kafka.entity.MediaEncodeJobStatus;
import com.onfilm.domain.kafka.metrics.MediaEncodeMetrics;
import com.onfilm.domain.kafka.entity.MediaEncodeJob;
import com.onfilm.domain.kafka.message.EncodeJobPreset;
import com.onfilm.domain.kafka.message.EncodeJobType;
import com.onfilm.domain.kafka.repository.MediaEncodeJobRepository;
import com.onfilm.domain.kafka.repository.MediaEncodeOutboxRepository;
import com.onfilm.domain.kafka.repository.MediaUploadRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MediaEncodeMaintenanceServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");
    private static final Duration JOB_TIMEOUT = Duration.ofHours(4).plusMinutes(30);

    @Mock MediaEncodeJobRepository jobRepository;
    @Mock MediaEncodeOutboxRepository outboxRepository;
    @Mock MediaUploadRequestRepository uploadRequestRepository;

    private MediaEncodeMaintenanceService service;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new MediaEncodeMaintenanceService(
                jobRepository,
                outboxRepository,
                uploadRequestRepository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new MediaEncodeMetrics(meterRegistry)
        );
        ReflectionTestUtils.setField(service, "jobTimeout", JOB_TIMEOUT);
    }

    @Test
    void usesConfiguredJobTimeoutAsCutoff() {
        List<MediaEncodeJobStatus> activeStatuses = List.of(
                MediaEncodeJobStatus.REQUESTED,
                MediaEncodeJobStatus.PROCESSING
        );
        given(jobRepository.findTop100ByStatusInAndRequestedAtBefore(
                activeStatuses,
                NOW.minus(JOB_TIMEOUT)
        )).willReturn(List.of());

        service.failTimedOutJobs();

        verify(jobRepository).findTop100ByStatusInAndRequestedAtBefore(
                activeStatuses,
                NOW.minus(JOB_TIMEOUT)
        );
    }

    @Test
    void recordsTimeoutAndTerminalMetricsForExpiredJobs() {
        MediaEncodeJob job = movieJob(NOW.minus(JOB_TIMEOUT).minusSeconds(1));
        given(jobRepository.findTop100ByStatusInAndRequestedAtBefore(
                List.of(MediaEncodeJobStatus.REQUESTED, MediaEncodeJobStatus.PROCESSING),
                NOW.minus(JOB_TIMEOUT)
        )).willReturn(List.of(job));

        assertThat(service.failTimedOutJobs()).isEqualTo(1);

        assertThat(job.getStatus()).isEqualTo(MediaEncodeJobStatus.FAILED);
        assertThat(meterRegistry.counter(
                "media.encode.job.timeout", "type", "movie").count()).isEqualTo(1);
        assertThat(meterRegistry.counter(
                "media.encode.job.terminal", "type", "movie", "result", "timeout").count())
                .isEqualTo(1);
    }

    private MediaEncodeJob movieJob(Instant requestedAt) {
        String requestId = UUID.randomUUID().toString();
        String jobId = UUID.randomUUID().toString();
        return MediaEncodeJob.requested(
                jobId, requestId, 1L, 2L,
                EncodeJobType.MOVIE, EncodeJobPreset.VIDEO_HLS_720P_2500K_AAC_96K,
                "bucket", "movie/1/raw/file/" + requestId + ".mp4",
                "bucket", "movie/1/file/" + jobId + "/index.m3u8",
                "video/mp4", "application/vnd.apple.mpegurl", requestedAt
        );
    }
}
