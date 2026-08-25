# OnFilm 문제 해결 사례 기록

- 문서 작성일: 2026-08-24
- 목적: 포트폴리오와 면접 답변을 만들 때 사용할 수 있는 기술적 근거와 의사결정 기록 보존

이 디렉터리는 구현 기능을 나열하는 공간이 아니라, 리팩토링 과정에서 발견한 문제와 해결 과정, 선택하지 않은 대안 및 트레이드오프를 기록한다. 작업일은 Git 커밋 날짜를 기준으로 하며, 날짜를 확인할 수 없는 문서는 작성일을 사용한다.

## 사례 목록

| 작업일 | 사례 | 핵심 주제 | 관련 커밋 |
|---|---|---|---|
| 2026-08-17~19 | [엔티티와 Aggregate 경계 정비](01-entity-aggregate-refactoring.md) | 불변식, 팩토리, 연관관계, 정규화, 컬렉션 캡슐화 | `df16ed6`~`1b1652f` |
| 2026-08-19 | [스토리지 키와 트랜잭션 이후 파일 삭제](02-storage-key-and-file-lifecycle.md) | storageKey, 소유권, AFTER_COMMIT, 보상 삭제 | `b677dea`, `1b1652f` |
| 2026-08-20 | [User 인증 정책과 데이터 무결성](03-user-auth-domain-integrity.md) | 값 객체, 정규화, BCrypt, UNIQUE 경쟁 조건 | `59cfc1b` |
| 2026-08-20 | [Refresh Token 회전과 재사용 방어](04-refresh-token-security.md) | 회전, 탈취 감지, REQUIRES_NEW, 낙관적 락 | `44a635e` |
| 2026-08-21 | [미디어 인코딩의 원자성과 멱등성](05-media-encode-outbox-and-worker.md) | Transactional Outbox, 상태 머신, Worker 멱등성, HMAC | `04eff88` |
| 2026-08-22 | [DTO와 계층별 검증 흐름 정비](06-dto-and-validation-boundaries.md) | record, Bean Validation, 도메인 불변식, DB 제약 | `5da05d3` |
| 2026-08-24 | [서비스 책임을 Command와 Query로 분리](07-service-command-query-separation.md) | 책임 분리, 소유권 검증, 미디어 워크플로 | `e1f3df3` |
| 2026-08-25 | [도메인 예외와 API 오류 응답 표준화](08-domain-exception-and-api-error-standardization.md) | DomainException, ErrorCode, 공통 오류 응답, 보안·Callback 필터 | `b6044cc`~`2e8f23a` |
| 2026-08-25 | [DB 트랜잭션과 외부 I/O 경계 분리](09-transaction-boundary-and-external-io.md) | 외부 I/O 분리, 잠금 최소화, 재검증, 보상 삭제, BCrypt | `0a5e93a`~`5808bdb` |

## 포트폴리오 작성 시 사용법

각 사례에서 다음 항목을 골라 한 문단으로 압축한다.

1. 어떤 장애 또는 유지보수 위험을 발견했는가
2. 단순 코드 정리가 아니라 어떤 불변식이나 실패 시나리오를 해결했는가
3. 대안 중 무엇을 선택했고 무엇을 감수했는가
4. 테스트나 DB 제약으로 어떻게 검증했는가
5. 결과적으로 코드의 변경 범위와 운영 위험이 어떻게 줄었는가

면접에서는 “패턴을 적용했다”보다 “어떤 실패를 막기 위해 그 패턴이 필요했는지”를 먼저 설명한다.

## 새 사례 작성 규칙

- 파일명: `NN-short-topic.md`
- 날짜: Git에서 확인한 작업일을 우선 기록
- 최소 항목: 문제, 원인, 해결, 트레이드오프, 검증
- 장애 수치나 성능 수치는 실제 측정값만 기록
- 구현하지 않은 개선안은 완료한 것처럼 쓰지 않고 후속 과제로 구분

[사례 작성 템플릿](case-template.md)을 복사해 사용한다.
