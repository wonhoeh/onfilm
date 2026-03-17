# Internal API 403 분석 보고서

## 목적

이 문서는 인코딩 워커가 API 서버의 내부 콜백 API를 호출할 때 `403 Forbidden`이 발생한 원인을 분석하고, 해결 방안을 보안 관점까지 포함해 정리한 보고서다.

대상 증상:

- 워커가 `PATCH /internal/api/media-jobs/{jobId}` 호출
- API 서버가 `403 Forbidden` 반환
- 워커 로그:
  - `CoreApiException: Failed to update media job ...`
  - `HttpClientErrorException$Forbidden: 403 Forbidden`

## 결론 요약

이번 문제의 직접 원인은 `인증`이 아니라 `CSRF 보호 필터`다.

현재 서버는 `/internal/api/**`를 `permitAll`로 열어두었지만, 별도의 `CsrfProtectionFilter`가 `PATCH` 요청에도 브라우저 요청과 동일한 CSRF 검사를 적용하고 있다. 워커는 브라우저가 아니기 때문에 `Origin`, `Referer`, CSRF 쿠키, CSRF 헤더를 보내지 않으며, 그 결과 내부 API 호출이 `403`으로 차단된다.

즉 핵심은 아래다.

- `SecurityConfig`의 `permitAll`은 인증/인가에 대한 설정이다.
- `CsrfProtectionFilter`는 별도로 모든 state-changing 요청을 검사한다.
- 내부 서버-서버 호출까지 브라우저 기준 CSRF 정책을 그대로 적용한 것이 문제다.

## 1. 현재 증상

워커는 아래 흐름으로 API 서버에 상태를 보고한다.

1. Kafka 메시지 consume
2. `PATCH /internal/api/media-jobs/{jobId}`로 `PROCESSING` 전송
3. 인코딩 수행
4. 결과 반영 API 호출
5. `DONE` 또는 `FAILED` 보고

그런데 실제로는 2번에서 `403`이 발생하고, 워커는 이를 치명 오류로 간주해 retry, 최종적으로 DLT까지 이동한다.

## 2. 왜 403이 발생하는가

## 2-1. 인증 문제가 아닌 이유

현재 서버 보안 설정에서는 `/internal/api/**`를 허용했다.

관련 위치:

- `src/main/java/com/onfilm/domain/common/config/SecurityConfig.java`

즉 스프링 시큐리티 기준으로는 “로그인 안 해도 접근 가능한 경로”로 열려 있다.

그런데도 `403`이 발생한 이유는 CSRF 필터가 별도로 동작하기 때문이다.

## 2-2. 실제 차단 지점

관련 위치:

- `src/main/java/com/onfilm/domain/common/config/CsrfProtectionFilter.java`

이 필터는 `GET`, `HEAD`, `OPTIONS`, `TRACE`를 제외한 모든 요청에 대해 아래를 검사한다.

1. `Origin` 또는 `Referer`가 현재 `Host`와 같은 출처인지
2. `XSRF-TOKEN` 쿠키가 있는지
3. `X-CSRF-TOKEN` 헤더가 있는지
4. 쿠키 값과 헤더 값이 일치하는지

이 중 하나라도 만족하지 않으면 바로 `403`을 반환한다.

## 2-3. 워커가 왜 이 조건을 만족할 수 없는가

워커는 브라우저가 아니라 서버 애플리케이션이다. 따라서 일반적으로 다음과 같다.

- 브라우저 쿠키가 없음
- `XSRF-TOKEN` 쿠키가 없음
- `X-CSRF-TOKEN` 헤더도 없음
- `Origin` / `Referer`도 없음

즉 현재 필터 정책은 “브라우저 기반의 사용자 요청”을 전제로 설계되어 있고, “서버-서버 내부 호출”은 고려하지 않았다.

## 3. CSRF가 무엇이고, 왜 여기서 오해가 생겼는가

CSRF는 Cross-Site Request Forgery의 약자다. 브라우저가 자동으로 쿠키를 보내는 성질을 악용해, 사용자가 모르는 사이에 다른 사이트가 상태 변경 요청을 보내게 만드는 공격이다.

예를 들어:

- 사용자가 `onfilm.com`에 로그인해서 세션/쿠키를 가지고 있음
- 공격 사이트가 사용자의 브라우저에서 `POST /api/people/...` 같은 요청을 보냄
- 브라우저는 쿠키를 자동으로 포함
- 서버가 추가 검증이 없으면 공격이 성공

그래서 브라우저 기반 요청에는 CSRF 방어가 중요하다.

하지만 워커는 다르다.

- 워커는 브라우저가 아님
- 사용자의 쿠키를 자동으로 보내지 않음
- 외부 사이트에서 워커를 “속여서” 요청 보내는 CSRF 시나리오가 성립하지 않음

즉 워커 호출에 브라우저용 CSRF 정책을 그대로 적용하면, 정상적인 내부 호출만 막게 된다.

## 4. 문제의 본질

현재 구조의 본질적인 문제는 “보호 기법의 목적과 호출 주체가 맞지 않는다”는 점이다.

- 브라우저 요청:
  - CSRF 방어가 필요함
- 서버-서버 내부 요청:
  - CSRF보다 별도 인증/인가 수단이 더 적절함

즉 `/internal/api/**`에 대해서는 CSRF를 그대로 쓰는 대신, 내부 호출에 맞는 다른 보호 방식으로 전환해야 한다.

## 5. 해결 방법 1: `/internal/api/**`를 CSRF 검사에서 제외

## 5-1. 방법 설명

`CsrfProtectionFilter.shouldNotFilter()`에서 `/internal/api/**` 경로를 예외 처리한다.

개념적으로는 아래와 같다.

```java
@Override
protected boolean shouldNotFilter(HttpServletRequest request) {
    String method = request.getMethod();
    if (SAFE_METHODS.contains(method)) return true;
    String path = request.getRequestURI();
    return path.startsWith("/auth/login")
            || path.startsWith("/auth/signup")
            || path.startsWith("/auth/refresh")
            || path.startsWith("/auth/logout")
            || path.startsWith("/internal/api/");
}
```

이렇게 하면 워커의 `PATCH /internal/api/...` 요청은 브라우저용 CSRF 검사를 건너뛰게 된다.

## 5-2. 장점

- 가장 빠르게 문제를 해결할 수 있다
- 워커 코드 변경이 거의 필요 없다
- 현재 구조에서 즉시 적용 가능하다
- 브라우저 요청 경로와 내부 경로를 명확히 분리할 수 있다

## 5-3. 단점

- `/internal/api/**`가 아무 보호 없이 열려 있으면 보안상 위험하다
- 현재 `SecurityConfig`에서도 `permitAll` 상태라면, 네트워크 경로만 닿으면 누구나 호출 가능할 수 있다
- 운영 환경에서는 “CSRF는 뺐지만 대체 보호가 없다”는 상태가 될 수 있다

## 5-4. 보안 평가

이 방법 자체가 틀린 것은 아니다. 서버-서버 호출에 CSRF를 적용하지 않는 것은 일반적이다.

문제는 “CSRF를 빼고 무엇으로 보호할 것인가”다.

따라서 이 방식은 아래 조건에서 유효하다.

- 내부망에서만 접근 가능
- API gateway / ingress / security group으로 접근 제한
- 혹은 곧바로 내부 토큰 방식이 추가될 예정

즉 이 방식은 빠른 장애 복구용으로는 매우 유효하지만, 장기적으로는 반드시 다른 보호 수단과 같이 가야 한다.

## 6. 해결 방법 2: `/internal/api/**`를 내부 토큰 또는 IP 제한으로 보호

## 6-1. 방법 설명

브라우저용 CSRF 대신, 서버-서버 호출에 적합한 인증 수단을 적용한다.

대표적인 방식:

1. 내부 토큰 헤더
   - 예: `X-INTERNAL-API-KEY: <secret>`
2. IP allowlist
   - 워커가 있는 내부 IP 또는 VPC 대역만 허용
3. 둘 다 적용
   - 네트워크 + 애플리케이션 이중 보호

예시 개념:

```java
String internalKey = request.getHeader("X-INTERNAL-API-KEY");
if (!expectedKey.equals(internalKey)) {
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    return;
}
```

혹은 프록시/로드밸런서 레벨에서 특정 IP만 `/internal/api/**` 접근 허용.

## 6-2. 장점

- 서버-서버 호출 목적에 맞는 보호 방식이다
- CSRF에 의존하지 않고 명시적인 내부 인증을 할 수 있다
- 운영 환경에서 훨씬 안전하다
- 내부 경로를 외부 사용자 API와 분리해서 생각할 수 있다

## 6-3. 단점

- 구현과 운영이 더 복잡하다
- 워커와 API 서버 양쪽 설정이 필요하다
- 비밀키 유출, 키 로테이션, 배포 환경 변수 관리 같은 운영 포인트가 생긴다
- IP 기반만 쓰면 NAT, 프록시, 환경 변경 시 관리가 번거로울 수 있다

## 6-4. 보안 평가

이 방식이 장기적으로 더 바람직하다.

이유:

- 브라우저 요청과 내부 요청의 보안 모델을 분리할 수 있다
- 누가 내부 API를 호출할 수 있는지 명시적으로 제어 가능하다
- 워커처럼 사람이 아닌 시스템 주체를 식별하기에 적합하다

가장 좋은 형태는 보통 아래다.

- `/internal/api/**`는 CSRF 제외
- 대신 `X-INTERNAL-API-KEY` 또는 서명 기반 인증 적용
- 추가로 LB / VPC / Security Group / ingress 에서 IP 제한

즉 애플리케이션 레벨과 네트워크 레벨을 함께 쓰는 구조가 가장 안정적이다.

## 7. 두 방법 비교

### 방법 1. CSRF 제외

적합한 상황:

- 로컬 개발
- 빠른 장애 복구
- 내부망이 이미 잘 닫혀 있음

장점:

- 빠르고 단순함

단점:

- 별도 보호가 없으면 위험

### 방법 2. 내부 토큰/IP 제한

적합한 상황:

- 운영 환경
- 외부 노출 가능성이 있는 환경
- 장기 유지 구조

장점:

- 내부 API 보안 모델에 더 적합

단점:

- 구현/운영 복잡도 증가

## 8. 권장 방안

현실적으로는 단계적으로 가는 것이 좋다.

### 1단계. 즉시 장애 해소

- `/internal/api/**`를 CSRF 검사에서 제외

효과:

- 워커의 `PATCH /internal/api/media-jobs/{jobId}` 호출이 즉시 정상화됨
- 현행 장애를 빠르게 해소 가능

### 2단계. 운영 보안 보강

- 내부 API 전용 헤더 토큰 도입
- 필요 시 IP allowlist 추가

효과:

- 브라우저용 보안 정책과 내부 서비스 보안 정책을 분리
- 운영 환경에서 무방비 상태를 피할 수 있음

## 9. 실무 권고안

현재 프로젝트에 가장 적합한 권고안은 아래다.

1. `/internal/api/**`는 `CsrfProtectionFilter` 예외 처리
2. `SecurityConfig`에서 해당 경로는 `permitAll` 대신 내부 토큰 필터와 함께 보호
3. 워커는 `X-INTERNAL-API-KEY` 헤더를 붙여 호출
4. 운영에서는 ingress 또는 네트워크 레벨에서 워커 대역만 접근 허용

이렇게 하면:

- 브라우저 API는 기존 CSRF 정책 유지
- 내부 API는 내부 전용 인증 체계 사용
- 각 요청 주체에 맞는 보안 정책 분리가 가능하다

## 10. 참고 코드 위치

- CSRF 필터:
  - `src/main/java/com/onfilm/domain/common/config/CsrfProtectionFilter.java`
- 보안 설정:
  - `src/main/java/com/onfilm/domain/common/config/SecurityConfig.java`
- 내부 콜백 API:
  - `src/main/java/com/onfilm/domain/kafka/controller/InternalMediaCallbackController.java`

## 11. 최종 요약

이번 `403`의 직접 원인은 워커가 잘못 호출해서가 아니라, API 서버가 내부 API 호출을 브라우저 요청처럼 취급해 CSRF를 강제했기 때문이다.

따라서 해결은 “CSRF를 더 잘 맞춘다”가 아니라 “내부 API에 맞는 보안 모델로 분리한다”가 핵심이다.

- 단기 해결:
  - `/internal/api/**`를 CSRF 검사에서 제외
- 장기 해결:
  - 내부 토큰 또는 IP 제한으로 내부 API를 보호

보안적으로는 장기 해결안이 더 바람직하고, 운영 환경에서는 반드시 그 방향으로 가는 것이 좋다.
