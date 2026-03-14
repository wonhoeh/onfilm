package com.onfilm.domain.kafka.config;

import com.onfilm.domain.kafka.message.MediaEncodeRequestedMessage;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
// Kafka 주소가 설정된 환경에서만 프로듀서를 활성화한다.
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
@EnableConfigurationProperties(KafkaTopicProperties.class)
public class KafkaProducerConfig {

    @Bean
    ProducerFactory<String, MediaEncodeRequestedMessage> mediaEncodeProducerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers
    ) {
        // 인코딩 요청 메시지 전용 프로듀서 설정.
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // 컨슈머를 단순 JSON 기반으로 만들기 위해 Spring 타입 헤더는 제외한다.
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    KafkaTemplate<String, MediaEncodeRequestedMessage> mediaEncodeKafkaTemplate(
            ProducerFactory<String, MediaEncodeRequestedMessage> mediaEncodeProducerFactory
    ) {
        return new KafkaTemplate<>(mediaEncodeProducerFactory);
    }
}
