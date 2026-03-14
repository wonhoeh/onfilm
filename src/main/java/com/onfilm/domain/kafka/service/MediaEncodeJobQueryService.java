package com.onfilm.domain.kafka.service;

import com.onfilm.domain.common.error.exception.MediaEncodeJobNotFoundException;
import com.onfilm.domain.common.util.SecurityUtil;
import com.onfilm.domain.kafka.dto.MediaEncodeJobStatusResponse;
import com.onfilm.domain.kafka.entity.MediaEncodeJob;
import com.onfilm.domain.kafka.repository.MediaEncodeJobRepository;
import org.springframework.stereotype.Service;

@Service
public class MediaEncodeJobQueryService {

    private final MediaEncodeJobRepository mediaEncodeJobRepository;

    public MediaEncodeJobQueryService(MediaEncodeJobRepository mediaEncodeJobRepository) {
        this.mediaEncodeJobRepository = mediaEncodeJobRepository;
    }

    // 클라이언트 polling 용 상태 조회.
    public MediaEncodeJobStatusResponse getJobStatus(String jobId) {
        MediaEncodeJob job = mediaEncodeJobRepository.findById(jobId)
                .orElseThrow(() -> new MediaEncodeJobNotFoundException(jobId));
        if (!job.getRequestedByUserId().equals(SecurityUtil.currentUserId())) {
            throw new IllegalStateException("FORBIDDEN_MEDIA_JOB_ACCESS");
        }
        return MediaEncodeJobStatusResponse.from(job);
    }
}
