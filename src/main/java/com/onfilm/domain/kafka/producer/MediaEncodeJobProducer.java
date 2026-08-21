package com.onfilm.domain.kafka.producer;

import com.onfilm.domain.kafka.message.MediaEncodeRequestedMessage;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

// 인코딩 요청 메시지 발행을 추상화한 인터페이스.
public interface MediaEncodeJobProducer {

    CompletableFuture<SendResult<String, MediaEncodeRequestedMessage>> send(MediaEncodeRequestedMessage message);
}
