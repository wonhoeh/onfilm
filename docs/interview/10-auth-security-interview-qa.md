# 인증·보안 면접 질문과 답변

- 작성일: 2026-09-08
- 구성: 질문 10개를 면접 답변·프로젝트 사례·주의사항으로 정리
- 기준: OnFilm의 토큰 발급·회전 코드, 쿠키 설정, CSRF 필터와 재사용 대응 정책
- 주의: 구현한 기능과 향후 보완할 사항을 구분한다. 이 문서는 보안 점검 완료나 모든 공격 차단을 의미하지 않는다.

## 1. Access Token과 Refresh Token의 책임을 왜 나누었는가?

### 면접 답변

“Access Token은 API 요청에서 사용자를 인증하고, Refresh Token은 Access Token을 새로 발급받는 데 사용합니다. 역할을 나누면 Access Token의 유효기간을 짧게 유지하면서도 사용자가 매번 비밀번호를 입력하지 않도록 할 수 있습니다. 대신 Refresh Token은 서버에서 사용 상태와 폐기 여부를 관리해야 합니다.”

### 프로젝트 사례

| 구분 | Access Token | Refresh Token |
|---|---|---|
| 목적 | API 요청의 인증 | 인증 토큰 재발급 |
| 형태 | 사용자 ID와 만료 시각이 들어 있는 서명된 JWT | 안전한 난수로 생성한 문자열 |
| 검증 | JWT 서명·만료 등 검증 | DB의 토큰 해시·만료·폐기 상태 확인 |
| 보관 | HttpOnly 쿠키 | HttpOnly 쿠키, DB에는 원문 대신 SHA-256 해시 |

Refresh Token을 일반 API의 인증 수단으로 사용하지 않는다. 쿠키 Path 기본값도 Access Token은 `/`, Refresh Token은 `/auth`로 구분한다. Path는 전송 범위를 줄이는 설정이지 독립적인 보안 경계는 아니다.

### 주의사항

- JWT는 형식이고 Access/Refresh는 역할이다. 모든 Refresh Token이 JWT일 필요는 없다.
- JWT 서명은 위변조 확인용이지 내용 암호화가 아니다. 비밀번호 같은 비밀 정보를 넣지 않는다.
- 로그인 확인과 특정 Movie를 수정할 권한 확인은 별개다. 인증 후에도 대상 자원의 권한 검사가 필요하다.

근거: [JwtProvider](../../src/main/java/com/onfilm/domain/auth/security/JwtProvider.java), [RefreshTokenService](../../src/main/java/com/onfilm/domain/token/service/RefreshTokenService.java), [AuthProperties](../../src/main/java/com/onfilm/domain/auth/config/AuthProperties.java)

## 2. Access Token 만료 시간을 짧게 두는 이유는?

### 면접 답변

“토큰이 유출됐을 때 사용할 수 있는 시간을 줄이기 위해서입니다. 서명과 만료만 확인하는 JWT는 서버에서 Refresh Token을 삭제해도 이미 발급한 Access Token이 곧바로 무효가 되지는 않습니다. 짧은 유효기간과 재발급 시 서버 검증을 조합해 위험과 로그인 편의성을 조절합니다.”

### 프로젝트 사례와 트레이드오프

- Access Token의 만료 시각은 `JwtProvider.createAccessToken()`에서 TTL을 받아 설정한다.
- 토큰 TTL은 설정값이므로 ‘항상 몇 분’이라고 외우기보다 해당 배포 설정을 확인한다.
- 너무 짧으면 재발급 요청과 동시 갱신 충돌이 늘고, 너무 길면 유출 토큰의 악용 가능 시간이 길어진다.
- Refresh Token 자체도 탈취되면 재발급이 가능하므로 회전·재사용 감지·안전한 저장이 함께 필요하다.
- 즉시 차단이 필요하다면 Access Token 차단 목록이나 서버의 세션 상태 검사 등을 별도로 검토해야 한다. 이는 조회 비용과 상태 관리 부담을 추가한다.

짧은 수명의 Access Token으로 유출 영향을 줄인다는 설계 배경은 [RFC 9700의 Refresh Token 보호 절](https://www.rfc-editor.org/rfc/rfc9700.html#section-4.14)에서도 설명한다. 이를 참고했다고 OnFilm이 OAuth 전체 규격을 구현한 것은 아니다.

## 3. Refresh Token Rotation이 무엇인가?

### 면접 답변

“토큰을 재발급할 때 기존 Refresh Token을 폐기하고 새 Refresh Token으로 교체하는 방식입니다. 같은 토큰을 계속 사용하는 대신 한 번 사용한 토큰은 다시 사용할 수 없게 합니다. 서버가 폐기 기록을 보관하면 이전 토큰이 다시 들어왔을 때 이상 사용을 감지할 수 있습니다.”

### 프로젝트 처리 순서

1. 요청의 원문 Refresh Token을 해시로 변환해 DB에서 찾는다.
2. 폐기 여부와 만료 시각을 확인한다.
3. 기존 토큰의 `consume()`을 호출해 사용·폐기 시각을 기록한다.
4. `saveAndFlush()`로 기존 토큰 변경과 버전 충돌을 확인한다.
5. 새로운 난수 토큰을 만들고 그 해시를 저장한다.
6. 기존 토큰 폐기와 새 토큰 저장을 하나의 트랜잭션으로 커밋한다.

### 주의사항

- flush는 commit이 아니다. 새 토큰 저장까지 실패하면 같은 트랜잭션의 기존 토큰 변경도 롤백된다.
- DB 커밋 후 응답이 유실되면 클라이언트는 새 토큰을 받지 못한 채 이전 토큰으로 재시도할 수 있다. 회전만으로 이 문제까지 해결되지는 않는다.
- 회전은 유출을 원천 차단하는 기술이 아니라, 이전 토큰의 재사용을 제한하고 감지하는 수단이다.

근거: [회전 구현](../../src/main/java/com/onfilm/domain/token/service/RefreshTokenService.java), [RefreshToken 엔티티](../../src/main/java/com/onfilm/domain/token/entity/RefreshToken.java), [RFC 9700의 Rotation 설명](https://www.rfc-editor.org/rfc/rfc9700.html#section-4.14.2)

## 4. 기존 Refresh Token 재사용을 어떻게 탐지하는가?

### 면접 답변

“요청 토큰의 해시로 DB 기록을 조회한 뒤, 이미 폐기된 토큰인지 확인합니다. OnFilm에서는 폐기된 토큰이 다시 들어오면 재사용으로 판단해 해당 사용자의 Refresh Token을 모두 삭제하고 401을 반환합니다. 보안 처리가 오류 응답 때문에 롤백되지 않도록 별도 트랜잭션에서 실행합니다.”

### 프로젝트 정책

- `revokedAt`이 있으면 폐기된 토큰으로 판단한다.
- `REQUIRES_NEW`의 `deleteAllSessionsForReuse()`에서 해당 사용자의 모든 Refresh Token을 삭제한다.
- 사용자 ID와 재사용 이벤트를 WARN 로그에 남기되 원문 토큰과 해시는 기록하지 않는다.
- 만료·폐기 토큰은 정책상 기본 30일 보존 후 정리한다. 다만 재사용 대응의 사용자 전체 삭제는 이보다 먼저 기록을 제거할 수 있다.
- 기록이 이미 삭제된 토큰은 유효하지 않은 토큰으로 거절하지만, 그 기록만으로 누구의 이전 토큰인지 추적할 수는 없다.

### 면접에서 함께 설명할 한계

- 재사용은 탈취 가능성을 뜻하지만, 정상 클라이언트의 중복 요청·응답 유실도 원인일 수 있다.
- 현재는 기기별 `familyId`나 `sessionId`가 아니라 사용자 단위로 처리한다. 단순하지만 다른 정상 기기의 재발급도 막힌다.
- ‘모든 세션 삭제’는 여기서 Refresh Token 기록 삭제를 뜻한다. 발급된 Access Token의 즉시 무효화까지 보장하지 않는다.
- 만료 토큰 접근도 별도 트랜잭션에서 사용·폐기 시각을 남긴 뒤 401을 반환한다. 이는 재사용 감지와 구분되는 분기다.

근거: [재사용 대응 정책](../decisions/refresh-token-reuse-policy.md), [보안 처리 전용 트랜잭션](../../src/main/java/com/onfilm/domain/token/service/RefreshTokenSecurityTransactionService.java), [만료 기록 보존 테스트](../../src/test/java/com/onfilm/domain/token/RefreshTokenExpirationPersistenceTest.java)

## 5. Refresh Token 동시 요청 문제는 왜 발생하는가?

### 면접 답변

“여러 API 요청이 동시에 Access Token 만료를 만나면 각각 재발급을 요청할 수 있습니다. 이때 같은 Refresh Token을 두 요청이 함께 사용합니다. 서버에서는 한 번만 회전되도록 동시성을 제어하고, 클라이언트에서는 진행 중인 재발급 요청 하나를 공유하는 방식으로 불필요한 중복을 줄여야 합니다.”

### 프로젝트의 서버 처리

- `RefreshToken`의 `@Version`으로 낙관적 락을 적용한다.
- 두 요청이 같은 버전을 읽어 수정하면, 먼저 커밋된 변경과 충돌하는 쪽은 성공할 수 없다.
- 회전 중 `saveAndFlush()`의 낙관적 락 충돌은 유효하지 않은 토큰 오류로 전환한다.
- 이미 첫 요청이 폐기를 완료한 후 두 번째 요청이 조회하면 낙관적 락 충돌이 아니라 재사용 분기로 들어갈 수 있다. 이 경우 사용자 전체 Refresh Token 삭제 정책이 적용된다.

### 보완 방향과 주의사항

- 클라이언트에서 하나의 갱신 Promise를 공유하고, 다른 요청은 갱신 완료를 기다리게 할 수 있다. 여러 탭이라면 탭 간 조율도 필요하다. 이는 서버 코드와 구분되는 보완 방향이다.
- 락 충돌이 났다고 같은 이전 토큰으로 무조건 재시도하면 재사용 감지를 유발할 수 있다.
- 짧은 유예 기간을 두는 대안은 정상 중복을 허용하지만 공격자의 재사용도 허용할 수 있어 별도 정책이 필요하다. 현재 적용했다고 설명하지 않는다.

근거: [동시 회전·재사용 테스트](../../src/test/java/com/onfilm/domain/token/RefreshTokenConcurrencyTest.java), [회전 서비스](../../src/main/java/com/onfilm/domain/token/service/RefreshTokenService.java)

## 6. HttpOnly의 역할은?

### 면접 답변

“브라우저 JavaScript가 쿠키 값을 직접 읽지 못하도록 하는 속성입니다. OnFilm에서는 Access Token과 Refresh Token 쿠키에 적용해 스크립트를 통한 토큰 탈취 위험을 줄였습니다. 다만 쿠키는 요청에 자동으로 포함되므로 HttpOnly만으로 XSS나 CSRF가 해결되지는 않습니다.”

### 프로젝트 사례

- 인증 쿠키: `httpOnly(true)`
- CSRF 쿠키: `httpOnly(false)` — 화면 코드가 값을 읽어 `X-CSRF-TOKEN` 헤더에 넣어야 하기 때문이다.
- CSRF 쿠키는 로그인 자격을 증명하는 인증 토큰과 다른 역할이다.

XSS가 발생하면 토큰 값을 읽지 못하더라도 사용자 권한으로 요청을 보낼 수 있다. [MDN HttpOnly 설명](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Set-Cookie#httponly)

근거: [AuthCookieFactory](../../src/main/java/com/onfilm/domain/auth/infrastructure/AuthCookieFactory.java)

## 7. Secure Cookie의 역할은?

### 면접 답변

“쿠키를 HTTPS 연결에서만 전송하도록 제한하는 속성입니다. 평문 HTTP로 인증 쿠키가 전달되는 위험을 줄입니다. 쿠키 내용을 암호화하는 속성은 아니며, JavaScript 접근을 제한하는 HttpOnly와도 역할이 다릅니다.”

### 프로젝트 사례와 주의사항

- `AuthCookieFactory`는 인증·CSRF 쿠키별 Secure 설정을 `AuthProperties`에서 받는다.
- 따라서 코드에 설정 항목이 있다는 사실과 실제 배포에서 활성화됐다는 사실을 구분해야 한다.
- 서비스 공개 시 HTTPS 구성과 실제 `Set-Cookie` 응답을 확인한다. 쿠키가 전송되지 않는다고 무조건 Secure를 끄지 않는다.

브라우저에는 localhost 예외가 있으므로 로컬에서의 동작을 일반 HTTP 배포에 그대로 적용하지 않는다. [MDN Secure 설명](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Set-Cookie#secure)

## 8. SameSite의 역할은?

### 면접 답변

“다른 사이트에서 시작한 요청에도 쿠키를 보낼지 정하는 속성입니다. 외부 사이트가 사용자의 인증 쿠키를 이용하는 상황을 줄일 수 있습니다. OnFilm은 기본값으로 Lax를 사용하지만, 이것만으로 모든 CSRF를 막는다고 보지는 않습니다.”

| 값 | 전송 범위 요약 |
|---|---|
| Strict | 같은 사이트에서 시작한 요청 |
| Lax | 같은 사이트 요청과 외부 링크 이동 같은 최상위 안전 메서드 요청 |
| None | 교차 사이트 요청도 허용. Secure 필수 |

‘사이트’와 ‘출처(origin)’는 다르다. 출처는 스킴·호스트·포트를 비교하지만, 사이트는 스킴과 등록 가능한 도메인을 기준으로 판단한다. 예를 들어 같은 HTTPS의 `app.example.com`과 `api.example.com`은 다른 출처여도 같은 사이트일 수 있다.

근거: [MDN SameSite 설명](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Set-Cookie#samesitesamesite-value), [프로젝트 기본값](../../src/main/java/com/onfilm/domain/auth/config/AuthProperties.java)

## 9. Cookie 기반 인증에서 CSRF 보호가 필요한 이유는?

### 면접 답변

“브라우저는 쿠키의 전송 조건이 맞으면 사용자의 의도와 관계없이 쿠키를 요청에 붙입니다. 공격자가 외부 페이지에서 요청을 유도하면 서버가 정상 사용자의 행동으로 오인할 수 있습니다. 따라서 상태를 바꾸는 요청은 인증뿐 아니라 CSRF 토큰이나 요청 출처도 검증해야 합니다.”

### 프로젝트의 실제 구현

- Spring Security 기본 CSRF 기능은 끄고 별도의 `CsrfProtectionFilter`를 사용한다. 따라서 설정의 `.csrf(disable)`만 보고 ‘CSRF 검사가 없다’고 설명하지 않는다.
- 필터 적용 대상에서 `Origin` 또는 `Referer`와 `Host`를 비교한다.
- CSRF 쿠키와 `X-CSRF-TOKEN` 헤더가 모두 있고 값이 같은지 확인한다. 쿠키와 헤더로 같은 값을 보내는 Double Submit Cookie 방식이다.
- 내부 Callback은 브라우저 쿠키 대신 HMAC 인증을 사용하므로 이 필터에서 제외하고 별도 인증을 유지한다.

### 현재 구현의 한계와 보완 검토 사항

- `/auth/login`, `/auth/signup`, `/auth/refresh`, `/auth/logout`로 시작하는 경로도 현재 필터에서 제외한다. ‘모든 상태 변경 요청을 CSRF로 보호했다’고 설명하면 부정확하다. 인증 경로의 공격 가능성과 제외 범위를 따로 검토해야 한다.
- 현재 출처 비교 함수는 스킴을 비교하지 않고 일부 분기에서 포트 차이도 허용한다. 엄격한 origin 검증을 완료한 것으로 표현하지 않는다.
- 현재는 쿠키·헤더의 단순 일치 검사이며, 로그인 세션에 결합된 서명형 CSRF 토큰은 아니다. 쿠키 주입 위협에 대한 보완 후보는 세션 결합형 서명 토큰이나 프레임워크 검증 기능이다.
- GET으로 데이터를 변경하지 않는 설계도 전제다. 필터에서 안전 메서드를 제외하기 때문이다.
- CORS만으로 CSRF를 대체하지 않는다. 응답을 읽지 못하는 요청도 서버의 상태를 바꿀 수 있다.

위 보완 방향은 [OWASP CSRF 예방 지침](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)을 참고한 검토 항목이며, 이번 문서 작성에서 코드를 변경한 것은 아니다.

근거: [CsrfProtectionFilter](../../src/main/java/com/onfilm/domain/auth/security/CsrfProtectionFilter.java), [SecurityConfig](../../src/main/java/com/onfilm/domain/auth/config/SecurityConfig.java), [필터 테스트](../../src/test/java/com/onfilm/domain/auth/security/CsrfProtectionFilterTest.java), [Callback과 CSRF 충돌 해결 사례](../problem-solving/12-production-callback-csrf-conflict.md)

## 10. XSS와 CSRF의 차이는?

### 면접 답변

“XSS는 공격자의 스크립트가 우리 사이트에서 실행되는 문제이고, CSRF는 사용자의 인증 정보가 자동으로 붙는 점을 이용해 원하지 않은 요청을 보내게 하는 문제입니다. XSS는 출력 위치에 맞는 이스케이프와 안전한 화면 구성으로 예방하고, CSRF는 요청 토큰과 출처 검증 등으로 방어합니다.”

### 차이를 설명하는 예시

| 구분 | XSS | CSRF |
|---|---|---|
| 예시 | 소개 글에 삽입된 악성 코드가 열람자의 브라우저에서 실행됨 | 외부 페이지가 로그인한 사용자의 정보 변경 요청을 유도함 |
| 핵심 문제 | 신뢰한 화면에서 공격자 코드가 실행됨 | 서버가 원하지 않은 요청을 사용자 행동으로 오인함 |
| 주요 대응 | 문맥별 출력 인코딩, 안전한 DOM API, 필요한 HTML 정화 | CSRF 토큰, 출처 검증, SameSite 등 |

### 프로젝트에 연결해 설명할 때

- HttpOnly는 토큰의 직접적인 JavaScript 접근을 줄이는 조치이지 XSS 자체를 제거한 근거가 아니다.
- 소개 글·제목 같은 입력을 화면에 표시하는 코드까지 확인해야 XSS 방어를 평가할 수 있다. 이번 문서에서는 프론트엔드 전체의 안전성을 검증했다고 주장하지 않는다.
- XSS가 생기면 스크립트가 CSRF 토큰을 읽고 요청할 수도 있다. 두 문제를 각각 방어해야 한다.
- CSP는 추가 방어 수단이며 안전한 출력 처리를 대신하지 않는다.

일반적인 출력·스크립트 방어 원칙은 [OWASP XSS 예방 지침](https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html)을 참고한다.

## 복습 순서

1. 토큰 역할 분리 → 짧은 만료 → 회전 → 재사용 감지 → 동시 요청
2. HttpOnly → Secure → SameSite → CSRF → XSS와의 차이
3. 프로젝트의 사용자 단위 폐기 정책, Access Token 즉시 폐기의 한계, CSRF 제외 경로를 추가 질문에 연결

관련 단원: [Java·Spring·JPA](08-java-spring-jpa-interview-qa.md), [DB·동시성](09-db-concurrency-interview-qa.md)
