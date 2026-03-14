package com.onfilm.domain.kafka.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// application.yml 에서 Kafka 토픽 이름을 읽어오는 설정 객체.
@ConfigurationProperties(prefix = "app.kafka.topic")
public record KafkaTopicProperties(
        String mediaEncodeRequested
) {
}
