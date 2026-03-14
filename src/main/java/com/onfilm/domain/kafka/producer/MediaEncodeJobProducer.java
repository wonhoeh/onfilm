package com.onfilm.domain.kafka.producer;

import com.onfilm.domain.kafka.message.MediaEncodeRequestedMessage;

// 인코딩 요청 메시지 발행을 추상화한 인터페이스.
public interface MediaEncodeJobProducer {

    void send(MediaEncodeRequestedMessage message);
}
