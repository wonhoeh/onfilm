# Internal API Token 설계 문서

## 목적

이 문서는 워커가 호출하는 `/internal/api/**` 경로를 브라우저용 CSRF 정책 대신 “내부 서비스 전용 토큰”으로 보호하기 위한 설계안이다.

대상 경로:

- `PATCH /internal/api/media-jobs/{jobId}`
- `PATCH /internal/api/movies/{movieId}/media`
- `PATCH /internal/api/trailers/{jobId}/media`

## 1. 왜 내부 토큰이 필요한가

현재 내부 API는 워커가 서버-서버 방식으로 호출한다.

이 경로는 브라우저 요청이 아니므로:

- CSRF 토큰/쿠키 기반 보호와 맞지 않고
- 대신 호출 주체가 “신뢰된 내부 워커인지”를 확인하는 방식이 더 적합하다

즉 내부 토큰은 아래 목적에 맞는다.

- 워커만 내부 API를 호출할 수 있게 제한
- 외부 사용자가 `/internal/api/**`를 임의 호출하지 못하게 차단
- 브라우저용 보안 모델과 내부 서비스 보안 모델을 분리

## 2. 기본 설계

### 헤더

워커는 모든 `/internal/api/**` 요청에 아래 헤더를 포함한다.

```http
X-INTERNAL-API-KEY: <secret>
```

### 서버 검증

API 서버는 `/internal/api/**` 요청이 들어오면:

1. 설정된 secret 존재 여부 확인
2. 요청 헤더의 `X-INTERNAL-API-KEY` 확인
3. 서버 설정값과 일치하면 통과
4. 다르면 `403 Forbidden`

## 3. 설정값 구조

예시:

```yaml
app:
  internal-api:
    enabled: true
    key: ${INTERNAL_API_KEY}
```

권장:

- `dev`: `.env`, local env var, IDE run config 로 주입
- `prod`: secret manager, 환경 변수, 배포 시스템 secret 사용

주의:

- Git에 하드코딩하지 않기
- `application-prod.yml`에 평문으로 넣지 않기

## 4. 서버 구현 위치 제안

### 4-1. 필터 방식

가장 단순한 구현은 `OncePerRequestFilter`를 하나 추가하는 것이다.

예시 이름:

- `InternalApiKeyFilter`

역할:

- `/internal/api/**` 요청만 검사
- `X-INTERNAL-API-KEY` 헤더 비교
- 불일치 시 `403`

### 4-2. 시큐리티 체인과의 관계

권장 구조:

1. `/internal/api/**`는 `CsrfProtectionFilter`에서 제외
2. 대신 `InternalApiKeyFilter`를 적용
3. 필요 시 `SecurityConfig`에서 `/internal/api/**`는 `authenticated`가 아니라 내부 필터 기반으로 제어

즉:

- 외부 사용자 API:
  - JWT / 로그인 / CSRF
- 내부 워커 API:
  - internal API key

## 5. 예시 구현 흐름

### 요청

```http
PATCH /internal/api/media-jobs/ff48708f-9cff-4a4c-bafa-e43313e72dc0
X-INTERNAL-API-KEY: my-secret-key
Content-Type: application/json

{
  "status": "PROCESSING",
  "startedAt": "2026-03-15T10:00:00Z"
}
```

### 서버 필터 로직 개념

```java
if (!request.getRequestURI().startsWith("/internal/api/")) {
    filterChain.doFilter(request, response);
    return;
}

String actual = request.getHeader("X-INTERNAL-API-KEY");
if (expectedKey == null || expectedKey.isBlank()) {
    response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    return;
}
if (!expectedKey.equals(actual)) {
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    return;
}

filterChain.doFilter(request, response);
```

## 6. 워커 측 변경 포인트

워커의 `CoreApiClient`는 모든 내부 API 호출에 같은 헤더를 추가하면 된다.

예:

```java
restClient.patch()
    .uri(...)
    .header("X-INTERNAL-API-KEY", internalApiKey)
    .body(...)
    .retrieve()
```

설정:

- `application-dev.yml`, `application-prod.yml` 또는 env 에서 같은 secret 주입

## 7. 장점

- 서버-서버 호출 목적에 맞는 보안 방식
- 구현이 비교적 단순함
- 브라우저 기반 CSRF와 독립적으로 운영 가능
- 워커 외 호출을 쉽게 차단 가능
- 로그에서 누가 내부 API를 호출했는지 추적하기 쉬움

## 8. 단점

- secret 유출 시 누구나 호출 가능
- 키 회전 정책이 필요함
- 서비스가 늘어나면 secret 배포 관리가 필요함
- 네트워크 레벨 보호 없이 이것만으로 충분하다고 보면 위험할 수 있음

## 9. 보안 강화 권장안

내부 토큰만 단독으로 쓰기보다 아래를 함께 권장한다.

### 9-1. IP allowlist

- ingress, nginx, ALB, security group, VPC 레벨에서 워커 IP 대역만 허용

장점:

- 토큰 유출 시에도 2차 방어 가능

### 9-2. HTTPS 강제

- 토큰이 평문 전송되지 않도록 반드시 TLS 사용

### 9-3. 키 로테이션

- old/new key를 일정 기간 동시 허용하는 구조 검토

예:

- `X-INTERNAL-API-KEY`가 현재 키 또는 이전 키면 허용
- 전환 완료 후 이전 키 제거

### 9-4. 감사 로그

- `/internal/api/**` 호출 시
  - path
  - remote addr
  - 성공/실패
  - jobId
를 남기면 운영 시 원인 파악이 쉬움

## 10. 단계적 적용 순서

### 1단계

- `/internal/api/**`를 CSRF 예외 처리
- 내부 토큰 필터 추가
- 워커가 헤더 붙이도록 수정

### 2단계

- `dev` 환경에서 워커-API 연동 테스트
- 잘못된 토큰일 때 `403` 확인
- 정상 토큰일 때 `204` 확인

### 3단계

- `prod`에 secret manager 기반 키 주입
- 필요 시 IP allowlist 추가

## 11. 장애 대응 관점

내부 토큰 방식 적용 후 아래를 빠르게 확인할 수 있어야 한다.

- 서버 설정값이 비어 있지 않은지
- 워커가 헤더를 실제로 보내는지
- 헤더 이름 오타 없는지
- dev/prod secret 값이 동일한지
- reverse proxy가 해당 헤더를 제거하지 않는지

## 12. 최종 권장안

현재 프로젝트에 가장 적합한 구조는 아래다.

1. `/internal/api/**`는 `CsrfProtectionFilter`에서 제외
2. `/internal/api/**`는 `InternalApiKeyFilter`로 보호
3. 워커는 `X-INTERNAL-API-KEY` 헤더를 포함해 호출
4. 운영에서는 IP allowlist도 같이 사용

즉 내부 API는 브라우저용 CSRF가 아니라, 내부 서비스 전용 토큰으로 보호하는 것이 맞다.
