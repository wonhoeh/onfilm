# 운영 환경 Worker Callback과 CSRF 정책 충돌

- 작업일: 2026-09-05
- 문서 작성일: 2026-09-05
- 관련 커밋: 코드 수정 `e68076e`, 문서 작성 `99576a6`
- 상태: 해결 및 AWS 전체 흐름 재검증 완료
- 문서 성격: AWS 검증 환경에서 운영 프로필로 실행하며 발견한 장애의 분석·복구 기록

이 사례에서 말하는 운영 환경은 실제 고객이 이용하는 상용 서비스가 아니라, 운영 프로필로 API와 Worker를 실행한 AWS 성능 검증 환경이다. 실제 상용 서비스의 사용자 장애로 확대해 표현하지 않는다.

## 문제

AWS 성능 측정 환경에 API, Worker, Kafka와 RDS를 배포한 뒤 테스트 영상을 업로드했다. API는 업로드 완료 요청에 `202 Accepted`를 반환하고 `MediaEncodeJob`과 Outbox를 저장했지만, Job 상태가 계속 `REQUESTED`에 머물렀다. 겉으로는 Kafka 발행 실패, Worker 장애, S3 다운로드 실패 또는 Callback 인증 실패 중 어느 구간의 문제인지 알 수 없었다.

정상이라면 영상 처리는 다음 순서로 완료돼야 했다.

```text
영상 업로드 완료
→ API가 Job과 Outbox 저장
→ 별도 발행기가 Kafka 메시지 발행
→ Worker가 메시지 소비
→ S3 파일 다운로드 및 ffmpeg 인코딩
→ Worker가 API에 처리 상태와 결과 Callback
→ API가 Job을 DONE으로 변경
```

업로드 완료 요청은 성공했지만 마지막 상태가 바뀌지 않았으므로, 전체 흐름을 한꺼번에 추측하지 않고 각 구간에 남은 기록을 차례로 확인했다.

## 기록 확인

Kafka의 Offset은 Topic 안에서 메시지의 위치를 나타내고, Consumer Lag은 아직 소비하지 못한 메시지 수를 뜻한다. Retry Topic은 다시 처리할 작업을, DLT(Dead Letter Topic)는 자동 처리를 계속하기 어려운 실패 메시지를 보관한다.

| 확인한 기록 | 관찰 결과 | 다음 판단 |
|---|---|---|
| API Job | `REQUESTED` 유지 | Worker의 처리 결과가 API 상태에 반영되지 않음 |
| 원본 Topic | End Offset 1 | API가 인코딩 요청을 Kafka에 발행함 |
| Worker Consumer | Current Offset 1, Lag 0 | Worker가 원본 메시지를 소비함 |
| Retry Topic | 모두 0건 | 일시적 실패 재시도 경로로 이동하지 않음 |
| DLT | 1건 | Worker 처리 과정에서 재시도로 해결하기 어려운 실패로 분류됨 |
| Callback Secret | API와 Worker의 SHA-256 동일 | Secret 오입력 가능성 제외 |
| Callback 보안 경로 | CSRF 검증 실패 시 403 반환 | Kafka 이후의 Callback과 보안 필터 조사 필요 |

이 결과로 Transactional Outbox와 Kafka 발행·소비 구간은 정상임을 확인했다. Retry Topic은 비어 있고 DLT에만 메시지가 있었으므로 Worker가 받은 뒤 발생한 영구 실패 경로로 조사 범위를 줄였다. Secret 원문을 출력하지 않고 SHA-256 결과만 비교해 설정값 불일치 여부를 확인했다.

당시 확인한 Offset, Lag과 Topic 건수는 남겼지만 Callback의 원본 HTTP 응답 본문과 발생 시각 전체는 보존하지 못했다. 따라서 공개 글에서는 보관하지 않은 로그를 재현된 운영 로그처럼 제시하지 않는다.

## 원인

운영 프로필의 `CsrfProtectionFilter`가 모든 상태 변경 요청에 Origin, CSRF Cookie와 Header를 요구하면서 `/internal/api/**`도 검사하고 있었다. 서버 간 통신인 Worker Callback은 브라우저 Cookie를 사용하지 않고 HMAC-SHA256 서명, Timestamp와 Nonce로 인증하므로 CSRF 값을 보내지 않는다. 그 결과 정상 HMAC Callback이 `InternalCallbackHmacFilter`에 도달하기 전에 CSRF 필터에서 403으로 차단됐다.

개발용 `DevCsrfProtectionFilter`만 내부 API를 제외하고 있어 로컬에서는 정상처럼 보였고, 운영 프로필에서만 장애가 발생했다. Worker는 API의 4xx 응답을 재시도로 해결할 수 없는 계약 위반으로 분류해 Retry Topic을 거치지 않고 DLT로 보냈다. `PROCESSING` Callback도 반영되지 않아 API Job은 `REQUESTED`에 남았다.

개발 환경과 운영 프로필의 차이는 다음과 같았다.

```text
dev 프로필
└── DevCsrfProtectionFilter가 /internal/api/**를 CSRF 검사에서 제외

prod 프로필
└── 공통 CsrfProtectionFilter가 /internal/api/**도 브라우저 요청처럼 검사
```

로컬 개발 프로필에서는 이미 내부 경로가 제외돼 같은 문제가 나타나지 않았다. 공통 정책이 아니라 개발 전용 하위 필터에 예외가 들어가 있었고, 운영 프로필의 전체 필터 체인을 통과하는 테스트가 부족했던 것이 환경별 차이를 놓친 이유였다.

## 해결

공통 `CsrfProtectionFilter.shouldSkipByPath()`에 `/internal/api/`를 추가해 개발·운영 프로필 모두 같은 정책을 사용하도록 변경했다. 개발 필터에 중복돼 있던 내부 API 예외는 제거했다.

```java
// 변경 전: 인증 API만 브라우저용 CSRF 검사에서 제외
return path.startsWith("/auth/login")
        || path.startsWith("/auth/signup")
        || path.startsWith("/auth/refresh")
        || path.startsWith("/auth/logout");
```

```java
// 변경 후: 서버 간 내부 Callback도 공통 정책에서 제외
return path.startsWith("/auth/login")
        || path.startsWith("/auth/signup")
        || path.startsWith("/auth/refresh")
        || path.startsWith("/auth/logout")
        || path.startsWith("/internal/api/");
```

내부 API의 인증을 생략한 것은 아니다. 브라우저 요청은 기존 Double Submit Cookie 방식의 CSRF 검증을 유지하고, Worker Callback은 기존 HMAC 서명·5분 Timestamp 허용 범위·Nonce 재사용 방지·본문 해시 검증을 계속 거친다. 즉 요청 주체에 맞지 않던 브라우저 보안 정책만 분리했다.

### CSRF를 제외해도 내부 API를 공개하지 않은 이유

CSRF는 브라우저가 인증 Cookie를 자동으로 보내는 특성을 악용한 요청을 방어한다. Worker는 브라우저가 아니며 사용자 Cookie를 사용하지 않으므로, CSRF Cookie와 Header를 발급하는 방식은 요청 주체와 맞지 않는다.

| 요청 주체 | 적용한 보호 방식 |
|---|---|
| 사용자 브라우저 | Same Origin 확인과 CSRF Cookie·Header 비교 |
| Worker Callback | HMAC-SHA256 서명, Timestamp, Nonce와 본문 해시 검증 |

Spring Security에서도 `/internal/api/**`는 `MEDIA_WORKER` 역할을 요구한다. 따라서 CSRF 제외는 내부 API의 인증을 없앤 변경이 아니라, 브라우저 요청과 서버 간 요청에 각자 맞는 보호 방식을 적용한 것이다.

## 기술 선택과 트레이드오프

### 선택한 방법

경로별 인증 성격을 분리했다. 사용자 상태 변경 API에는 CSRF를 적용하고, 내부 Callback에는 HMAC 인증을 적용한다. 역할이 명확하고 Worker가 브라우저 Cookie에 의존하지 않는다.

### 검토한 대안

Worker에 CSRF Cookie와 Header를 발급하는 방법은 서버 간 호출에 브라우저 세션 개념을 끌어들이고 Secret 관리만 복잡하게 만든다. CSRF 필터보다 HMAC 필터를 먼저 실행하는 것만으로는 HMAC 인증 후 다시 CSRF에서 거부되므로 해결되지 않는다. 내부 API 전체를 인증 없이 허용하는 방법은 위조 Callback 위험 때문에 제외했다.

### 감수한 비용

보안 필터마다 담당 경로를 명시적으로 관리해야 한다. 새로운 내부 API를 추가할 때 HMAC 필터 적용 범위와 CSRF 제외 범위가 일치하는지 회귀 테스트가 필요하다.

## 검증

- `CsrfProtectionFilterTest.internalCallbackSkipsBrowserCsrfValidation`을 추가해 운영 프로필에서 사용하는 공통 필터가 내부 Callback을 다음 필터로 전달하는지 확인
- Kafka 원본 Topic End Offset 1, Worker Current Offset 1, Lag 0 확인
- Retry Topic 0건, DLT 1건 확인
- API·Worker Callback Secret의 SHA-256 일치 확인
- 운영 CSRF 필터의 내부 Callback 통과 테스트 추가
- 기존 HMAC 서명, Timestamp, 본문 변조와 Nonce 재사용 거부 테스트 재실행
- 관련 테스트 `BUILD SUCCESSFUL`
- AWS 재배포 후 새 Job이 `DONE`으로 완료되고 Callback이 API 상태에 반영됨
- 요청부터 완료까지 약 6.62초, Worker 처리 약 4.44초 확인

수정 전후의 관찰 결과는 다음과 같다.

| 구분 | 수정 전 | 수정 후 |
|---|---|---|
| 업로드 완료 요청 | `202 Accepted` | `202 Accepted` |
| Kafka 발행·소비 | 성공 | 성공 |
| Worker Callback | CSRF 검사에서 차단 | HMAC 인증 후 정상 처리 |
| API Job | `REQUESTED` 유지 | `DONE` |
| 전체 처리 결과 | DLT 1건 | 새 Job 정상 완료 |

수정 후 새 Job의 요청부터 완료까지 약 6.62초, Worker 처리 구간은 약 4.44초였다. 이 값은 장애 수정 후 한 건의 전체 흐름을 확인한 결과이며 처리 성능의 평균이나 보장 시간으로 사용하지 않는다.

## 결과

Kafka 문제처럼 보이던 현상을 Offset, Lag, Retry와 DLT를 기준으로 구간별 분리해 실제 원인을 운영 보안 필터까지 좁혔다. 개발·운영 프로필의 정책 차이를 제거했고, 브라우저 CSRF와 서버 간 HMAC 인증의 책임을 명확히 했다.

수정 배포 후 `API → Outbox → Kafka → Worker → S3 → HMAC Callback → API DONE` 전체 흐름을 새 Job으로 재검증했다. Job `7c45a841-dcb7-4f15-8345-b15d982ca9c3`은 실패 코드 없이 `DONE`으로 종료됐다.

## 재발 방지

- `/internal/api/**`의 CSRF 제외 정책을 개발 전용 필터가 아니라 공통 필터에서 관리한다.
- 내부 Callback이 브라우저용 CSRF 검사에서는 제외되지만 HMAC 인증과 `MEDIA_WORKER` 권한 검사를 계속 거치는지 함께 확인한다.
- HMAC 서명, Timestamp 범위, 본문 변조와 Nonce 재사용 거부 테스트를 유지한다.
- 같은 장애가 발생하면 `API Job → Outbox → Kafka Offset·Lag → Retry → DLT → Callback 응답` 순서로 확인한다.
- 운영 프로필의 전체 보안 필터 체인을 통과하는 통합 테스트를 후속 과제로 관리한다.
- 로그나 지표를 근거로 사용하려면 HTTP 상태, 오류 코드와 발생 시각을 함께 보존한다.

## 후속 과제

- 기존 DLT Job의 실패 상태 정리 및 재처리 여부 기록
- 필터 체인 전체를 통과하는 운영 프로필 통합 테스트 추가
- 장애 발생·복구 시각과 Callback 원본 HTTP 상태·오류 코드 자동 보존
- Callback 실패율과 DLT 증가를 연결한 경보의 실제 탐지·복구 훈련

## 포트폴리오 요약 후보

AWS 환경에서 인코딩 Job이 `REQUESTED`에 멈춘 문제를 Kafka Offset과 Consumer Lag, Retry Topic과 DLT 순서로 추적해 Kafka 전달은 정상임을 확인했습니다. 운영 CSRF 필터가 HMAC 기반 Worker Callback을 먼저 차단하는 프로필 간 정책 차이를 찾아, 사용자 API는 CSRF로 보호하고 내부 API는 HMAC·Timestamp·Nonce로 인증하도록 책임을 분리했습니다.
