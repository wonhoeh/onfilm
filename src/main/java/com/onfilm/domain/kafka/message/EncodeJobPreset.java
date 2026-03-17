package com.onfilm.domain.kafka.message;

// 컨슈머가 어떤 인코딩 규격으로 처리해야 하는지 나타낸다.
public enum EncodeJobPreset {
    VIDEO_HLS_720P_2500K_AAC_96K,
    THUMBNAIL_1280X720
}
