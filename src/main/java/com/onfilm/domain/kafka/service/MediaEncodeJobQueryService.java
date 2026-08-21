package com.onfilm.domain.kafka.service;

import com.onfilm.domain.common.error.exception.MediaEncodeJobNotFoundException;
import com.onfilm.domain.common.util.SecurityUtil;
import com.onfilm.domain.kafka.dto.MediaEncodeJobStatusResponse;
import com.onfilm.domain.kafka.entity.MediaEncodeJob;
import com.onfilm.domain.kafka.repository.MediaEncodeJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MediaEncodeJobQueryService {

    private final MediaEncodeJobRepository mediaEncodeJobRepository;

    public MediaEncodeJobQueryService(MediaEncodeJobRepository mediaEncodeJobRepository) {
        this.mediaEncodeJobRepository = mediaEncodeJobRepository;
    }

    // 클라이언트 polling 용 상태 조회.
    @Transactional(readOnly = true)
    public MediaEncodeJobStatusResponse getJobStatus(String jobId) {
        MediaEncodeJob job = mediaEncodeJobRepository.findByIdAndRequestedByUserId(
                        jobId, SecurityUtil.currentUserId())
                .orElseThrow(() -> new MediaEncodeJobNotFoundException(jobId));
        return MediaEncodeJobStatusResponse.from(job);
    }
}
