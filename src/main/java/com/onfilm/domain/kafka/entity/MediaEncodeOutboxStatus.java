package com.onfilm.domain.kafka.entity;

public enum MediaEncodeOutboxStatus {
    PENDING,
    PUBLISHING,
    PUBLISHED,
    DEAD
}
