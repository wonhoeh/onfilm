package com.onfilm.domain.kafka.entity;

import com.onfilm.domain.kafka.message.EncodeJobPreset;
import com.onfilm.domain.kafka.message.EncodeJobType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class MediaEncodeJobTest {
    private static final Instant REQUESTED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void validatesIdentityPresetContentTypeAndDistinctKeys() {
        assertThatThrownBy(() -> job("not-uuid", EncodeJobType.MOVIE,
                EncodeJobPreset.VIDEO_HLS_720P_2500K_AAC_96K, "video/mp4", "target"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> job(UUID.randomUUID().toString(), EncodeJobType.THUMBNAIL,
                EncodeJobPreset.VIDEO_HLS_720P_2500K_AAC_96K, "image/jpeg", "target"))
                .hasMessage("preset does not match jobType");
        assertThatThrownBy(() -> job(UUID.randomUUID().toString(), EncodeJobType.MOVIE,
                EncodeJobPreset.VIDEO_HLS_720P_2500K_AAC_96K, "image/jpeg", "target"))
                .hasMessage("sourceContentType does not match jobType");
    }

    @Test
    void preservesFirstTimestampsForRepeatedSameStateCallbacks() {
        MediaEncodeJob job = validMovieJob();
        Instant started = REQUESTED_AT.plusSeconds(10);
        Instant completed = REQUESTED_AT.plusSeconds(20);

        job.markProcessing(started);
        job.markProcessing(started.plusSeconds(5));
        job.markDone(completed);
        job.markDone(completed.plusSeconds(5));

        assertThat(job.getStartedAt()).isEqualTo(started);
        assertThat(job.getCompletedAt()).isEqualTo(completed);
        assertThat(job.getStatus()).isEqualTo(MediaEncodeJobStatus.DONE);
    }

    @Test
    void allowsRequestedToDoneButRejectsConflictingTerminalTransition() {
        MediaEncodeJob job = validMovieJob();
        job.markDone(REQUESTED_AT.plusSeconds(10));

        assertThatThrownBy(() -> job.markFailed("FAILED", "reason", REQUESTED_AT.plusSeconds(20)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("INVALID_MEDIA_JOB_STATUS_TRANSITION");
    }

    @Test
    void validatesTimestampOrderAndFailureLengths() {
        MediaEncodeJob job = validMovieJob();
        assertThatThrownBy(() -> job.markProcessing(REQUESTED_AT.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> job.markFailed(" ", "reason", REQUESTED_AT.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private MediaEncodeJob validMovieJob() {
        return job(UUID.randomUUID().toString(), EncodeJobType.MOVIE,
                EncodeJobPreset.VIDEO_HLS_720P_2500K_AAC_96K, "video/mp4", "target");
    }

    private MediaEncodeJob job(String jobId, EncodeJobType type, EncodeJobPreset preset,
                               String sourceContentType, String targetSuffix) {
        String requestId = UUID.randomUUID().toString();
        return MediaEncodeJob.requested(
                jobId, requestId, 1L, 2L, type, preset,
                "bucket", "movie/1/raw/file/" + requestId + ".mp4",
                "bucket", "movie/1/file/" + targetSuffix + "/index.m3u8",
                sourceContentType,
                type == EncodeJobType.THUMBNAIL ? "image/jpeg" : "application/vnd.apple.mpegurl",
                REQUESTED_AT
        );
    }
}
