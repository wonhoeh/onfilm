package com.onfilm.domain.kafka.message;

// 컨슈머가 어떤 인코딩 규격으로 처리해야 하는지 나타낸다.
public enum EncodeJobPreset {
    MOVIE_720P_3000K,
    TRAILER_720P_3000K,
    THUMBNAIL_1280X720
}
