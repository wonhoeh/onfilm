package com.onfilm.domain.kafka.service;

import com.onfilm.domain.kafka.entity.MediaEncodeJobStatus;
import com.onfilm.domain.kafka.repository.MediaEncodeJobRepository;
import com.onfilm.domain.kafka.repository.MediaEncodeOutboxRepository;
import com.onfilm.domain.kafka.repository.MediaUploadRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

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

    @BeforeEach
    void setUp() {
        service = new MediaEncodeMaintenanceService(
                jobRepository,
                outboxRepository,
                uploadRequestRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
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
}
