package com.onfilm.domain.kafka.entity;

// 인코딩 작업이 현재 어느 단계에 있는지 나타낸다.
public enum MediaEncodeJobStatus {
    REQUESTED,
    PROCESSING,
    DONE,
    FAILED
}
