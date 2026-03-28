# Kafka 개념

## 1. Kafka란

Kafka는 대용량 이벤트 스트림을 안정적으로 전달하기 위한 분산 메시징 시스템이다.

쉽게 말하면:

- 어떤 시스템이 메시지를 발행하고
- 다른 시스템이 그 메시지를 읽어서 처리하는 구조

를 안정적으로 만들기 위한 도구다.

## 2. 왜 Kafka를 쓰는가

Kafka를 쓰는 가장 큰 이유는 비동기 처리와 시스템 간 결합도 완화다.

예를 들어 어떤 요청을 처리할 때:

- 사용자 응답은 빨리 끝내고 싶고
- 무거운 후처리는 나중에 처리하고 싶으면

Kafka에 메시지를 남기고, 별도 consumer가 처리하게 만들 수 있다.

즉:

- 응답 속도 개선
- 시스템 분리
- 재처리/확장성 확보

를 위해 자주 사용된다.

## 3. 기본 구성 요소

### 3-1. Producer

메시지를 발행하는 쪽이다.

예:

- 주문 생성 이벤트 발행
- 결제 완료 이벤트 발행
- 인코딩 요청 이벤트 발행

### 3-2. Topic

메시지가 쌓이는 논리적 채널이다.

예:

- `order.created`
- `payment.completed`
- `media.encode.requested`

### 3-3. Consumer

토픽의 메시지를 읽어서 처리하는 쪽이다.

예:

- 주문 후처리
- 배송 연동
- 알림 발송
- 인코딩 수행

### 3-4. Partition

토픽은 여러 partition으로 나뉠 수 있다.

partition을 나누면:

- 병렬 처리 가능
- 처리량 증가

대신 같은 key를 같은 partition으로 보내야 순서가 필요한 메시지를 맞출 수 있다.

## 4. Kafka를 언제 쓰는가

### 4-1. 비동기 처리

요청-응답 안에서 다 처리하면 너무 무거운 작업일 때

예:

- 인코딩
- 알림 발송
- 분석 로그 적재
- 주문 후처리

### 4-2. 시스템 분리

API 서버와 후처리 시스템을 느슨하게 연결하고 싶을 때

### 4-3. 재처리 필요

실패한 작업을 다시 처리해야 하는 경우

## 5. Kafka를 쓸 때 중요한 개념

### 5-1. at-least-once

Kafka consumer는 같은 메시지를 한 번 이상 처리할 가능성이 있다.

즉 중복 소비 가능성을 전제로 멱등성을 고려해야 한다.

### 5-2. 멱등성

같은 메시지를 여러 번 받아도 결과가 한 번 처리한 것과 같아야 한다.

예:

- 같은 주문 완료 이벤트가 두 번 들어와도 배송 생성은 한 번만
- 같은 media job 완료 이벤트가 두 번 들어와도 상태는 한 번만 완료 처리

### 5-3. 순서 보장

Kafka는 같은 partition 안에서는 순서를 보장하지만, 전체 토픽 단위로 항상 순서를 보장하는 것은 아니다.

즉 순서가 중요한 이벤트는 같은 key를 써서 같은 partition으로 보내는 전략이 필요하다.

### 5-4. 재시도와 DLT

consumer 처리 실패 시:

- 재시도
- retry topic
- DLT(dead letter topic)

구조를 둘 수 있다.

운영에서는 실패 메시지를 버리기보다 추적 가능하게 남기는 것이 중요하다.

## 6. Kafka의 장점

- 무거운 작업을 비동기로 분리 가능
- producer / consumer 결합도 완화
- consumer를 늘려 확장 가능
- 재시도와 후처리 구조 설계 가능

## 7. Kafka의 단점

- 구조가 복잡해짐
- 즉시 일관성이 어려워짐
- 중복 처리, 순서, 재시도 정책을 직접 설계해야 함
- 운영 난이도가 올라감

즉 단순 CRUD나 즉시성만 중요한 시스템에 무조건 Kafka를 쓰는 것은 오버엔지니어링이 될 수 있다.

## 8. 현재 프로젝트와 연결할 수 있는 부분

현재 프로젝트에서는 Kafka가 실제로 적용되어 있다.

### 8-1. Producer

[`MediaEncodeJobCommandService.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/kafka/service/MediaEncodeJobCommandService.java)

역할:

- 인코딩 요청 메시지 발행
- `media_job` 상태 스냅샷 저장

즉 API 서버는 요청을 받은 뒤 Kafka로 메시지를 보내고, 무거운 인코딩은 직접 하지 않는다.

### 8-2. Consumer

현재 repo 안에는 worker 본체가 없지만, 설계상 별도 worker consumer가 메시지를 받아 ffmpeg 인코딩을 수행한다.

즉 구조는:

- API 서버가 producer
- worker 서버가 consumer

형태다.

### 8-3. 상태 추적

[`MediaEncodeJob.java`](/Users/whheo/Desktop/onfilm/onfilm/src/main/java/com/onfilm/domain/kafka/entity/MediaEncodeJob.java)

Kafka 메시지 자체만 믿지 않고:

- REQUESTED
- PROCESSING
- DONE
- FAILED

상태를 DB에 저장해 추적한다.

이건 비동기 처리에서 매우 중요한 포인트다.

### 8-4. callback API

worker는 처리 후 내부 API로 상태를 다시 반영한다.

즉 Kafka만으로 끝내지 않고:

- 메시지 발행
- worker 처리
- callback으로 DB 반영

흐름을 완성한 구조다.

## 9. 현재 프로젝트에서 말할 수 있는 장점

현재 프로젝트를 기준으로 Kafka 경험을 이렇게 설명할 수 있다.

- 무거운 인코딩 작업을 요청-응답 경로에서 분리했다
- API 서버와 worker 서버 책임을 분리했다
- 상태 추적 테이블로 비동기 작업을 가시화했다
- callback API로 최종 상태 반영 구조를 설계했다

## 10. 현재 프로젝트에서 아직 아쉬운 부분

과장 없이 보면 아래는 더 보완 가능한 지점이다.

- 메시지 멱등성에 대한 더 강한 보장
- consumer 재처리 정책 고도화
- DLT 운영 전략 정리
- 순서/중복/재시도 정책 명문화

즉 Kafka를 “썼다”보다 “비동기 처리 구조를 어떻게 닫았는가” 중심으로 설명하는 게 좋다.

## 11. 면접에서 말하기 좋은 문장

`Kafka는 무거운 작업을 요청-응답 경로에서 분리하고, producer와 consumer를 느슨하게 연결하기 위해 사용한다고 이해하고 있습니다. 현재 프로젝트에서도 업로드 이후 인코딩을 API 서버가 직접 처리하지 않고 Kafka로 메시지를 발행한 뒤 worker가 비동기로 처리하도록 분리했습니다. 또한 media_job 테이블과 internal callback API를 통해 비동기 작업 상태를 추적하도록 설계했습니다. 다만 Kafka는 중복 처리와 재시도, 멱등성 설계가 중요하기 때문에, 실제 운영에서는 그 부분까지 함께 설계해야 한다고 생각합니다.`

## 12. 한 줄 요약

Kafka는 비동기 처리와 시스템 분리를 위해 사용하는 메시징 시스템이며, 멱등성·재시도·상태 추적까지 함께 설계해야 제대로 쓸 수 있다.
