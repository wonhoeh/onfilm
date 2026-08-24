package com.onfilm.domain.kafka.service;

import com.onfilm.domain.common.error.ErrorCode;
import com.onfilm.domain.common.error.exception.InvalidMediaJobStatusTransitionException;
import com.onfilm.domain.common.error.exception.MediaOutputFileNotFoundException;
import com.onfilm.domain.file.service.StorageKeyPolicy;
import com.onfilm.domain.file.service.StorageService;
import com.onfilm.domain.kafka.dto.MediaEncodeCompletionRequest;
import com.onfilm.domain.kafka.dto.MediaEncodeFailureRequest;
import com.onfilm.domain.kafka.entity.MediaEncodeJob;
import com.onfilm.domain.kafka.entity.MediaEncodeJobStatus;
import com.onfilm.domain.kafka.message.EncodeJobPreset;
import com.onfilm.domain.kafka.message.EncodeJobType;
import com.onfilm.domain.kafka.repository.MediaEncodeJobRepository;
import com.onfilm.domain.movie.entity.AgeRating;
import com.onfilm.domain.movie.entity.Movie;
import com.onfilm.domain.movie.repository.MovieRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaEncodeJobInternalServiceTest {
    @Mock MediaEncodeJobRepository jobRepository;
    @Mock MovieRepository movieRepository;
    @Mock StorageKeyPolicy storageKeyPolicy;
    @Mock StorageService storageService;
    @InjectMocks MediaEncodeJobInternalService service;

    @Test
    void completionAppliesTrailerAndDoneAtomicallyAndIsIdempotent() {
        Instant requestedAt = Instant.parse("2026-01-01T00:00:00Z");
        String target = "movie/1/trailer/550e8400-e29b-41d4-a716-446655440000/index.m3u8";
        MediaEncodeJob job = trailerJob(target, requestedAt);
        Movie movie = Movie.create("Test Movie", 120, 2020, "movie-key", null, AgeRating.ALL);
        given(jobRepository.findById(job.getId())).willReturn(Optional.of(job));
        given(movieRepository.findById(1L)).willReturn(Optional.of(movie));
        given(storageService.exists(target)).willReturn(true);
        MediaEncodeCompletionRequest request = new MediaEncodeCompletionRequest(
                "bucket", target, "application/vnd.apple.mpegurl", requestedAt.plusSeconds(30));

        service.complete(job.getId(), request);
        service.complete(job.getId(), request);

        assertThat(job.getStatus()).isEqualTo(MediaEncodeJobStatus.DONE);
        assertThat(movie.getTrailers()).extracting(trailer -> trailer.getStorageKey()).containsExactly(target);
        verify(jobRepository).saveAndFlush(job);
    }

    @Test
    void rejectsCompletionWhoseOutputDoesNotMatchJob() {
        MediaEncodeJob job = trailerJob(
                "movie/1/trailer/550e8400-e29b-41d4-a716-446655440000/index.m3u8",
                Instant.parse("2026-01-01T00:00:00Z"));
        given(jobRepository.findById(job.getId())).willReturn(Optional.of(job));

        assertThatThrownBy(() -> service.complete(job.getId(), new MediaEncodeCompletionRequest(
                "bucket", "movie/2/trailer/other/index.m3u8",
                "application/vnd.apple.mpegurl", job.getRequestedAt().plusSeconds(1))))
                .hasMessage("callback output does not match media encode job");
        verifyNoInteractions(movieRepository);
    }

    @Test
    void terminalStateConflictIsRejected() {
        MediaEncodeJob job = trailerJob(
                "movie/1/trailer/550e8400-e29b-41d4-a716-446655440000/index.m3u8",
                Instant.parse("2026-01-01T00:00:00Z"));
        given(jobRepository.findById(job.getId())).willReturn(Optional.of(job));
        service.fail(job.getId(), new MediaEncodeFailureRequest(
                "ENCODE_FAILED", "codec failed", job.getRequestedAt().plusSeconds(1)));

        assertThatThrownBy(() -> service.complete(job.getId(), new MediaEncodeCompletionRequest(
                job.getTargetBucket(), job.getTargetKey(), job.getTargetContentType(),
                job.getRequestedAt().plusSeconds(2))))
                .isInstanceOfSatisfying(InvalidMediaJobStatusTransitionException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_MEDIA_JOB_STATUS_TRANSITION));
    }

    @Test
    void completionRejectsMissingEncodedOutputObject() {
        MediaEncodeJob job = trailerJob(
                "movie/1/trailer/550e8400-e29b-41d4-a716-446655440000/index.m3u8",
                Instant.parse("2026-01-01T00:00:00Z"));
        given(jobRepository.findById(job.getId())).willReturn(Optional.of(job));
        given(storageService.exists(job.getTargetKey())).willReturn(false);

        assertThatThrownBy(() -> service.complete(job.getId(), new MediaEncodeCompletionRequest(
                job.getTargetBucket(),
                job.getTargetKey(),
                job.getTargetContentType(),
                job.getRequestedAt().plusSeconds(1)
        ))).isInstanceOfSatisfying(MediaOutputFileNotFoundException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.MEDIA_OUTPUT_FILE_NOT_FOUND));
        verifyNoInteractions(movieRepository);
    }

    private MediaEncodeJob trailerJob(String target, Instant requestedAt) {
        String requestId = UUID.randomUUID().toString();
        return MediaEncodeJob.requested(
                UUID.randomUUID().toString(), requestId, 1L, 2L,
                EncodeJobType.TRAILER, EncodeJobPreset.VIDEO_HLS_720P_2500K_AAC_96K,
                "bucket", "movie/1/raw/trailer/" + requestId + ".mp4",
                "bucket", target, "video/mp4", "application/vnd.apple.mpegurl", requestedAt);
    }
}
