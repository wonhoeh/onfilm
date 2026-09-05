# 운영 환경 Worker Callback과 CSRF 정책 충돌

- 작업일: 2026-09-05
- 문서 작성일: 2026-09-05
- 관련 커밋: 커밋 전
- 상태: 일부 완료 — 코드 수정·단위 테스트 완료, AWS 전체 흐름 재검증 예정
- 문서 성격: 약 2,000자 초안이며 운영 재검증 후 로그·수치·타임라인을 보강한다.

## 문제

AWS 성능 측정 환경에 API, Worker, Kafka와 RDS를 배포한 뒤 테스트 영상을 업로드했다. API는 업로드 완료 요청에 `202 Accepted`를 반환하고 `MediaEncodeJob`과 Outbox를 저장했지만, Job 상태가 계속 `REQUESTED`에 머물렀다. 겉으로는 Kafka 발행 실패, Worker 장애, S3 다운로드 실패 또는 Callback 인증 실패 중 어느 구간의 문제인지 알 수 없었다.

Kafka Offset을 확인하자 원본 Topic에는 1건이 발행됐고 Worker Consumer의 Offset도 1, Lag은 0이었다. 따라서 Transactional Outbox 발행과 Kafka 전달·소비는 성공했다. 반면 Retry Topic은 모두 0건이고 DLT에 1건이 존재했다. 이는 Worker가 메시지를 받았지만 재시도하지 않는 영구 실패로 분류했다는 뜻이었다. API와 Worker의 Callback Secret을 원문 대신 SHA-256으로 비교한 결과도 동일해 단순 Secret 오입력은 배제했다.

## 원인

운영 프로필의 `CsrfProtectionFilter`가 모든 상태 변경 요청에 Origin, CSRF Cookie와 Header를 요구하면서 `/internal/api/**`도 검사하고 있었다. 서버 간 통신인 Worker Callback은 브라우저 Cookie를 사용하지 않고 HMAC-SHA256 서명, Timestamp와 Nonce로 인증하므로 CSRF 값을 보내지 않는다. 그 결과 정상 HMAC Callback이 `InternalCallbackHmacFilter`에 도달하기 전에 CSRF 필터에서 403으로 차단됐다.

개발용 `DevCsrfProtectionFilter`만 내부 API를 제외하고 있어 로컬에서는 정상처럼 보였고, 운영 프로필에서만 장애가 발생했다. Worker는 API의 4xx 응답을 재시도로 해결할 수 없는 계약 위반으로 분류해 Retry Topic을 거치지 않고 DLT로 보냈다. `PROCESSING` Callback도 반영되지 않아 API Job은 `REQUESTED`에 남았다.

## 해결

공통 `CsrfProtectionFilter.shouldSkipByPath()`에 `/internal/api/`를 추가해 개발·운영 프로필 모두 같은 정책을 사용하도록 변경했다. 개발 필터에 중복돼 있던 내부 API 예외는 제거했다.

내부 API의 인증을 생략한 것은 아니다. 브라우저 요청은 기존 Double Submit Cookie 방식의 CSRF 검증을 유지하고, Worker Callback은 기존 HMAC 서명·5분 Timestamp 허용 범위·Nonce 재사용 방지·본문 해시 검증을 계속 거친다. 즉 요청 주체에 맞지 않던 브라우저 보안 정책만 분리했다.

## 기술 선택과 트레이드오프

### 선택한 방법

경로별 인증 성격을 분리했다. 사용자 상태 변경 API에는 CSRF를 적용하고, 내부 Callback에는 HMAC 인증을 적용한다. 역할이 명확하고 Worker가 브라우저 Cookie에 의존하지 않는다.

### 검토한 대안

Worker에 CSRF Cookie와 Header를 발급하는 방법은 서버 간 호출에 브라우저 세션 개념을 끌어들이고 Secret 관리만 복잡하게 만든다. CSRF 필터보다 HMAC 필터를 먼저 실행하는 것만으로는 HMAC 인증 후 다시 CSRF에서 거부되므로 해결되지 않는다. 내부 API 전체를 인증 없이 허용하는 방법은 위조 Callback 위험 때문에 제외했다.

### 감수한 비용

보안 필터마다 담당 경로를 명시적으로 관리해야 한다. 새로운 내부 API를 추가할 때 HMAC 필터 적용 범위와 CSRF 제외 범위가 일치하는지 회귀 테스트가 필요하다.

## 검증

- Kafka 원본 Topic End Offset 1, Worker Current Offset 1, Lag 0 확인
- Retry Topic 0건, DLT 1건 확인
- API·Worker Callback Secret의 SHA-256 일치 확인
- 운영 CSRF 필터의 내부 Callback 통과 테스트 추가
- 기존 HMAC 서명, Timestamp, 본문 변조와 Nonce 재사용 거부 테스트 재실행
- 관련 테스트 `BUILD SUCCESSFUL`
- AWS 재배포 후 `REQUESTED → PROCESSING → DONE`, S3 HLS 결과와 Callback 반영은 후속 검증 예정

## 결과

Kafka 문제처럼 보이던 현상을 Offset, Lag, Retry와 DLT를 기준으로 구간별 분리해 실제 원인을 운영 보안 필터까지 좁혔다. 개발·운영 프로필의 정책 차이를 제거했고, 브라우저 CSRF와 서버 간 HMAC 인증의 책임을 명확히 했다.

## 후속 과제

- API 재배포 후 새 Job으로 전체 파이프라인 재검증
- 기존 DLT Job의 실패 상태 정리 및 재처리 여부 기록
- 운영 로그의 HTTP 상태와 `errorCode` 확인 내용 보강
- 필터 체인 전체를 통과하는 운영 프로필 통합 테스트 추가 검토
- 실제 장애 발생·복구 시각과 처리 시간 기록

## 포트폴리오 요약 후보

AWS 환경에서 인코딩 Job이 `REQUESTED`에 멈춘 문제를 Kafka Offset과 Consumer Lag, Retry Topic과 DLT 순서로 추적해 Kafka 전달은 정상임을 확인했습니다. 운영 CSRF 필터가 HMAC 기반 Worker Callback을 먼저 차단하는 프로필 간 정책 차이를 찾아, 사용자 API는 CSRF로 보호하고 내부 API는 HMAC·Timestamp·Nonce로 인증하도록 책임을 분리했습니다.
