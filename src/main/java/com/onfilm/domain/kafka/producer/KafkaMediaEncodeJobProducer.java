package com.onfilm.domain.kafka.producer;

import com.onfilm.domain.kafka.config.KafkaTopicProperties;
import com.onfilm.domain.kafka.message.MediaEncodeRequestedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
// 실제 Kafka 토픽으로 인코딩 요청 메시지를 보내는 구현체.
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
@RequiredArgsConstructor
public class KafkaMediaEncodeJobProducer implements MediaEncodeJobProducer {

    private final KafkaTemplate<String, MediaEncodeRequestedMessage> kafkaTemplate;
    private final KafkaTopicProperties topicProperties;

    @Override
    public void send(MediaEncodeRequestedMessage message) {
        // jobId 를 메시지 키로 사용해 같은 작업의 추적 기준을 맞춘다.
        kafkaTemplate.send(topicProperties.mediaEncodeRequested(), message.jobId(), message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish media encode job. jobId={}", message.jobId(), ex);
                        return;
                    }
                    log.info(
                            "Published media encode job. topic={}, partition={}, offset={}, jobId={}",
                            result.getRecordMetadata().topic(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset(),
                            message.jobId()
                    );
                });
    }
}
