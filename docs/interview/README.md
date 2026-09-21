# 면접 준비 문서 목록

문서 파일명에 작성 시점이 오래된 순서대로 번호를 붙였다. 본문 내용은 당시 작성 시점을 기준으로 하므로 최신 구현과 차이가 있을 수 있다.

## 번호와 날짜 기준

- 문서에 작성일 또는 작성 기준일이 있으면 그 날짜를 우선한다.
- 날짜가 없는 기존 문서는 최초 Git 등록일을 사용한다. 이는 실제 작성일을 확정한 날짜가 아니다.
- 01~06은 같은 커밋(`4234435`, 2026-03-18)에 처음 등록돼 선후관계를 확인할 수 없으므로 기존 영문 파일명 순으로 정렬했다.
- 08~13은 모두 2026-09-08 작성이며 대화에서 확인되는 작성 순서(Java·Spring·JPA → DB·동시성 → 인증·보안 → Kafka·운영 → 종합 질문 → 종합 답변)를 유지했다.
- 이 README는 탐색용 목록이므로 번호를 붙이지 않는다. 이후 문서는 다음 번호를 사용하고 작성일을 함께 기록한다.

## 작성 시점별 목록

| 번호 | 날짜 기준 | 문서 | 날짜 근거 |
|---|---|---|---|
| 01 | 2026-03-18 | [DB 인덱스와 쿼리 단축 가이드](01-db-index-guide.md) | 최초 Git 등록일 |
| 02 | 2026-03-18 | [엔티티 연관관계 정리](02-entity-relationship-guide.md) | 최초 Git 등록일 |
| 03 | 2026-03-18 | [K6 부하테스트 가이드](03-k6-guide.md) | 최초 Git 등록일 |
| 04 | 2026-03-18 | [관계형 DB 역량 어필 문장 / 개선 과제 정리](04-rdbms-appeal-and-improvement-guide.md) | 최초 Git 등록일 |
| 05 | 2026-03-18 | [병목을 뚫는 서버 코드 최적화 가이드](05-server-optimization-jpa-nplus1.md) | 최초 Git 등록일 |
| 06 | 2026-03-18 | [트랜잭션과 락 전략 가이드](06-transaction-lock-strategy.md) | 최초 Git 등록일 |
| 07 | 2026-08-17 | [3년차 백엔드 개발자 역량 진단 및 보완 계획](07-backend-three-year-growth-plan.md) | 문서 작성 기준일 |
| 08 | 2026-09-08 | [Java·Spring·JPA 면접 질문과 답변](08-java-spring-jpa-interview-qa.md) | 문서 작성일 |
| 09 | 2026-09-08 | [DB·동시성 면접 질문과 답변](09-db-concurrency-interview-qa.md) | 문서 작성일 |
| 10 | 2026-09-08 | [인증·보안 면접 질문과 답변](10-auth-security-interview-qa.md) | 문서 작성일 |
| 11 | 2026-09-08 | [Kafka·운영 면접 질문과 답변](11-kafka-operations-interview-qa.md) | 문서 작성일 |
| 12 | 2026-09-08 | [OnFilm 프로젝트 면접 질문 총정리](12-onfilm-project-interview-question-bank.md) | 문서 작성일 |
| 13 | 2026-09-08 | [OnFilm 프로젝트 면접 답변 총정리](13-onfilm-project-interview-answers.md) | 문서 작성일 |
| 14 | 2026-09-14 | [백엔드 서버·네트워크·인프라 학습 순서](14-backend-server-network-infrastructure-study-roadmap.md) | 문서 작성일 |
| 15 | 2026-09-14 | [Linux와 서버 기본](15-linux-server-fundamentals.md) | 문서 작성일 |

## 면접 연습 시작점

먼저 [종합 질문 260개](12-onfilm-project-interview-question-bank.md)에서 준비할 질문을 고르고, 같은 번호의 [종합 답변](13-onfilm-project-interview-answers.md)을 확인한다. 자세한 개념과 프로젝트 사례는 08~11 단원별 질문·답변에서 확인한다.
