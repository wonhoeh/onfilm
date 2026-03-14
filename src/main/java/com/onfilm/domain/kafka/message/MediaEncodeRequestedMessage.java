package com.onfilm.domain.kafka.message;

import java.time.Instant;

// 프로듀서가 Kafka에 넣는 "인코딩 요청서" 본문.
public record MediaEncodeRequestedMessage(
        String jobId,
        Long movieId,
        Long requestedByUserId,
        EncodeJobType jobType,
        EncodeJobPreset preset,
        String sourceBucket,
        String sourceKey,
        String targetBucket,
        String targetKey,
        String contentType,
        Instant requestedAt
) {
}
