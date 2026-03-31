# 프로젝트 기술 리뷰

## Kafka - 비동기 미디어 인코딩 파이프라인

### 전체 흐름

사용자가 업로드 요청을 하면 API 서버에서 presigned URL을 발급해줍니다. 사용자는 해당 URL로 S3에 직접 업로드하고, 완료되면 API 서버에 알립니다. API 서버는 Kafka로 인코딩 요청 메시지를 발행하고, worker가 메시지를 받아 인코딩 작업을 진행합니다. 진행하면서 Core API 서버에 작업 상태를 보고하고, Core API 서버는 `media_encode_job` 테이블로 작업 상태를 추적합니다.

### 상태 전이 가드

상태 변경 메서드(`markProcessing`, `markDone`, `markFailed`)를 엔티티 안에 두고, 서비스나 컨트롤러가 직접 status를 바꾸지 못하게 했습니다. 상태 전이 규칙이 한 곳에 모여 있어서 나중에 상태가 추가되거나 규칙이 바뀔 때 영향 범위를 파악하기 쉽습니다.

### 중복 callback 멱등성

Kafka at-least-once 특성상 같은 callback이 두 번 올 수 있어서, DONE→DONE, FAILED→FAILED처럼 동일한 terminal 상태의 중복 callback은 무시해서 멱등성을 확보했습니다. 반면 DONE→FAILED처럼 서로 다른 terminal 상태로 전이 시도하는 건 정상적인 재시도가 아닌 시스템 오류로 보고 예외 처리했습니다.

### 중복 consume 방어

worker가 중복 consume으로 같은 job을 두 번 시작하려 할 때, `markProcessing`이 실패하면 API 서버가 409를 반환합니다. worker는 409를 받으면 이미 처리 중인 job으로 인식하고 skip해서 재시도 없이 종료합니다. 이렇게 Core API와 worker가 상태 코드 계약으로 중복 실행을 막는 구조입니다.
