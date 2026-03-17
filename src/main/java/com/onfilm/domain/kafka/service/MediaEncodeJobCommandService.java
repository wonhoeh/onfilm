package com.onfilm.domain.kafka.service;

import com.onfilm.domain.kafka.entity.MediaEncodeJob;
import com.onfilm.domain.kafka.message.EncodeJobPreset;
import com.onfilm.domain.kafka.message.EncodeJobType;
import com.onfilm.domain.kafka.message.MediaEncodeRequestedMessage;
import com.onfilm.domain.kafka.producer.MediaEncodeJobProducer;
import com.onfilm.domain.kafka.repository.MediaEncodeJobRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
// 업로드 완료 시점에 인코딩 작업 메시지를 만들고 발행하는 서비스.
@ConditionalOnBean(MediaEncodeJobProducer.class)
public class MediaEncodeJobCommandService {

    private final MediaEncodeJobProducer producer;
    private final MediaEncodeJobRepository mediaEncodeJobRepository;

    public MediaEncodeJobCommandService(MediaEncodeJobProducer producer,
                                        MediaEncodeJobRepository mediaEncodeJobRepository) {
        this.producer = producer;
        this.mediaEncodeJobRepository = mediaEncodeJobRepository;
    }

    @Transactional
    public String requestMovieEncoding(Long movieId, Long requestedByUserId, String sourceBucket, String sourceKey,
                                       String targetBucket, String targetKey, String contentType) {
        return send(movieId, requestedByUserId, EncodeJobType.MOVIE, EncodeJobPreset.VIDEO_HLS_720P_2500K_AAC_96K,
                sourceBucket, sourceKey, targetBucket, targetKey, contentType);
    }

    @Transactional
    public String requestTrailerEncoding(Long movieId, Long requestedByUserId, String sourceBucket, String sourceKey,
                                         String targetBucket, String targetKey, String contentType) {
        return send(movieId, requestedByUserId, EncodeJobType.TRAILER, EncodeJobPreset.VIDEO_HLS_720P_2500K_AAC_96K,
                sourceBucket, sourceKey, targetBucket, targetKey, contentType);
    }

    @Transactional
    public String requestThumbnailEncoding(Long movieId, Long requestedByUserId, String sourceBucket, String sourceKey,
                                           String targetBucket, String targetKey, String contentType) {
        return send(movieId, requestedByUserId, EncodeJobType.THUMBNAIL, EncodeJobPreset.THUMBNAIL_1280X720,
                sourceBucket, sourceKey, targetBucket, targetKey, contentType);
    }

    private String send(Long movieId, Long requestedByUserId, EncodeJobType jobType, EncodeJobPreset preset,
                        String sourceBucket, String sourceKey, String targetBucket, String targetKey, String contentType) {
        // DB 작업 상태와 Kafka 메시지를 연결할 때 사용할 고유 작업 ID.
        String jobId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();
        mediaEncodeJobRepository.save(MediaEncodeJob.requested(
                jobId,
                movieId,
                requestedByUserId,
                jobType,
                preset,
                sourceBucket,
                sourceKey,
                targetBucket,
                targetKey,
                contentType,
                requestedAt
        ));
        producer.send(new MediaEncodeRequestedMessage(
                jobId,
                movieId,
                requestedByUserId,
                jobType,
                preset,
                sourceBucket,
                sourceKey,
                targetBucket,
                targetKey,
                contentType,
                requestedAt
        ));
        return jobId;
    }
}
