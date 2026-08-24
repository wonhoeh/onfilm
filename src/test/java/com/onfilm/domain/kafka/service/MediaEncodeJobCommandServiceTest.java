package com.onfilm.domain.kafka.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onfilm.domain.common.error.ErrorCode;
import com.onfilm.domain.common.error.exception.MediaEncodeJobNotFoundException;
import com.onfilm.domain.common.error.exception.MediaUploadRequestNotFoundException;
import com.onfilm.domain.file.service.StorageKeyPolicy;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.kafka.entity.*;
import com.onfilm.domain.kafka.message.EncodeJobType;
import com.onfilm.domain.kafka.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaEncodeJobCommandServiceTest {
    @Mock MediaEncodeJobRepository jobRepository;
    @Mock MediaUploadRequestRepository uploadRepository;
    @Mock MediaEncodeOutboxRepository outboxRepository;
    @Mock StorageService storageService;
    @Mock StorageKeyPolicy storageKeyPolicy;
    private MediaEncodeJobCommandService service;
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        service = new MediaEncodeJobCommandService(
                jobRepository, uploadRepository, outboxRepository,
                storageService, storageKeyPolicy,
                new ObjectMapper().findAndRegisterModules(), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void savesJobAndOutboxInOneCommandAndCompletesUploadRequest() {
        String requestId = UUID.randomUUID().toString();
        String source = "movie/1/raw/file/" + requestId + ".mp4";
        MediaUploadRequest upload = upload(requestId, source);
        given(uploadRepository.findByIdForUpdate(requestId)).willReturn(Optional.of(upload));
        given(storageService.exists(source)).willReturn(true);

        String jobId = requestMovie(requestId, source);

        assertThat(upload.getStatus()).isEqualTo(MediaUploadRequestStatus.COMPLETED);
        assertThat(upload.getJobId()).isEqualTo(jobId);
        ArgumentCaptor<MediaEncodeJob> jobCaptor = ArgumentCaptor.forClass(MediaEncodeJob.class);
        verify(jobRepository).save(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getRequestId()).isEqualTo(requestId);
        verify(outboxRepository).save(argThat(outbox ->
                outbox.getJobId().equals(jobId)
                        && outbox.getPayload().contains(jobId)
                        && outbox.getStatus() == MediaEncodeOutboxStatus.PENDING));
    }

    @Test
    void repeatedCompletionReturnsExistingJobWithoutCreatingAnotherOutbox() {
        String requestId = UUID.randomUUID().toString();
        String source = "movie/1/raw/file/" + requestId + ".mp4";
        MediaUploadRequest upload = upload(requestId, source);
        given(uploadRepository.findByIdForUpdate(requestId)).willReturn(Optional.of(upload));
        given(storageService.exists(source)).willReturn(true);
        String jobId = requestMovie(requestId, source);
        MediaEncodeJob saved = captureSavedJob();
        given(jobRepository.findById(jobId)).willReturn(Optional.of(saved));

        assertThat(requestMovie(requestId, source)).isEqualTo(jobId);
        verify(outboxRepository, times(1)).save(any());
        verify(jobRepository, times(1)).save(any());
    }

    @Test
    void missingUploadedObjectDoesNotCreateJobOrOutbox() {
        String requestId = UUID.randomUUID().toString();
        String source = "movie/1/raw/file/" + requestId + ".mp4";
        given(uploadRepository.findByIdForUpdate(requestId)).willReturn(Optional.of(upload(requestId, source)));
        given(storageService.exists(source)).willReturn(false);

        assertThatThrownBy(() -> requestMovie(requestId, source))
                .hasMessage("uploaded source object does not exist");
        verifyNoInteractions(jobRepository, outboxRepository);
    }

    @Test
    void missingUploadRequestThrowsNotFoundException() {
        String requestId = UUID.randomUUID().toString();
        String source = "movie/1/raw/file/" + requestId + ".mp4";
        given(uploadRepository.findByIdForUpdate(requestId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> requestMovie(requestId, source))
                .isInstanceOfSatisfying(MediaUploadRequestNotFoundException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_UPLOAD_REQUEST_NOT_FOUND));
        verifyNoInteractions(jobRepository, outboxRepository, storageService);
    }

    @Test
    void completedUploadWithMissingJobThrowsEncodeJobNotFoundException() {
        String requestId = UUID.randomUUID().toString();
        String source = "movie/1/raw/file/" + requestId + ".mp4";
        MediaUploadRequest upload = upload(requestId, source);
        given(uploadRepository.findByIdForUpdate(requestId)).willReturn(Optional.of(upload));
        given(storageService.exists(source)).willReturn(true);
        String jobId = requestMovie(requestId, source);
        clearInvocations(jobRepository, outboxRepository, storageService);
        given(jobRepository.findById(jobId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> requestMovie(requestId, source))
                .isInstanceOfSatisfying(MediaEncodeJobNotFoundException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_ENCODE_JOB_NOT_FOUND));
        verify(jobRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    private String requestMovie(String requestId, String source) {
        return service.requestMovieEncoding(
                requestId, 1L, 2L, "bucket", source, "bucket",
                "movie/1/file/550e8400-e29b-41d4-a716-446655440000/index.m3u8", "video/mp4");
    }

    private MediaUploadRequest upload(String requestId, String source) {
        return MediaUploadRequest.issue(
                requestId, 2L, 1L, EncodeJobType.MOVIE, "bucket", source, "video/mp4",
                now.minusSeconds(10), now.plusSeconds(600));
    }

    private MediaEncodeJob captureSavedJob() {
        ArgumentCaptor<MediaEncodeJob> captor = ArgumentCaptor.forClass(MediaEncodeJob.class);
        verify(jobRepository).save(captor.capture());
        return captor.getValue();
    }
}
