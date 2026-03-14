package com.onfilm.domain.kafka.controller;

import com.onfilm.domain.kafka.dto.MediaEncodeJobStatusResponse;
import com.onfilm.domain.kafka.service.MediaEncodeJobQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/media-jobs")
public class MediaEncodeJobController {

    private final MediaEncodeJobQueryService mediaEncodeJobQueryService;

    // 클라이언트가 jobId 로 현재 인코딩 상태를 polling 하는 API.
    @GetMapping("/{jobId}")
    public ResponseEntity<MediaEncodeJobStatusResponse> getJobStatus(@PathVariable String jobId) {
        return ResponseEntity.ok(mediaEncodeJobQueryService.getJobStatus(jobId));
    }
}
