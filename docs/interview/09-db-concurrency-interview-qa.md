# DB·동시성 면접 질문과 답변

- 작성일: 2026-09-08
- 구성: 최초 질문 1~13번, 추가 예상 질문 14~34번을 면접 답변·프로젝트 사례·주의사항으로 정리
- 기준: MySQL InnoDB, Spring·JPA와 OnFilm의 실제 코드·Migration·검증 기록
- 활용 방법: 면접 답변으로 핵심을 설명하고 추가 질문에 예시와 한계를 연결한다.
- 주의: 락과 인덱스 동작은 DB 종류·버전·격리 수준·데이터 분포에 영향을 받는다. 아래 성능 수치는 MySQL 8.4.11 전용 벤치마크의 기록이지 운영 API 성능 보장이 아니다.

## 1. Entity에서 중복을 검사해도 DB Unique Constraint가 필요한 이유는?

### 면접 답변

“엔티티의 중복 검사는 현재 객체가 알고 있는 데이터 안에서 동작하므로 다른 요청이 동시에 추가하는 데이터까지 막을 수 없습니다. 두 요청이 모두 중복이 없다고 확인한 뒤 저장할 수 있어, 동시 요청에서도 중복을 막으려면 DB Unique Constraint가 필요합니다.”

### 동시 요청 예시

1. 요청 A가 같은 참여 관계가 없는 것을 확인한다.
2. 요청 B도 같은 참여 관계가 없는 것을 확인한다.
3. A와 B가 각각 INSERT를 시도한다.
4. UNIQUE가 있으면 같은 키를 가진 두 행이 모두 커밋되는 것을 막는다. 한쪽이 커밋하면 다른 쪽은 중복 오류로 실패할 수 있다.

### 프로젝트 사례와 주의사항

- `movie_person(movie_id, person_id)`: 같은 작품·인물의 참여 중복 방지
- `movie_person_role(movie_person_id, role)`: 같은 참여 관계의 동일 역할 중복 방지
- `person_gallery(person_id, image_key)`: 같은 인물의 이미지 중복 방지
- 사전 검사는 빠르고 이해하기 쉬운 오류를 제공하고, DB는 경쟁 상황의 저장 무결성을 지킨다.
- `synchronized`는 같은 JVM의 해당 락에만 적용되므로 여러 서버의 중복 방지를 대체하지 못한다.
- UNIQUE 위반이 flush·commit에서 드러날 수 있으므로 예외 처리 경계도 확인한다. 모든 무결성 예외를 중복 오류로 바꾸지는 않는다.

근거: [Constraint 검사 기록](../review/database/mysql-constraint-audit.md), [V1 Migration](../../src/main/resources/db/migration/V1__create_initial_schema.sql), [V4 Migration](../../src/main/resources/db/migration/V4__strengthen_aggregate_constraints.sql)

## 2. Unique Constraint에 Nullable Column이 포함되면 어떤 문제가 발생할 수 있는가?

### 면접 답변

“MySQL의 UNIQUE는 NULL 값을 여러 개 허용합니다. 따라서 복합 UNIQUE의 일부 컬럼이 NULL이면, 애플리케이션에서는 같은 의미라고 보는 행도 여러 개 저장될 수 있습니다. NULL을 허용하는 이유와 어떤 조합을 중복으로 볼지를 먼저 정해야 합니다.”

### 예시

```sql
-- 개념 예시: 이 제약만으로는 아래 두 행을 차단하지 못한다.
UNIQUE (movie_id, genre_id)

-- movie_id | genre_id
--        1 | NULL
--        1 | NULL
```

NULL을 빈 문자열이나 0과 같은 값으로 취급해서 설명하지 않는다. 다른 DB에서는 NULL의 UNIQUE 처리 정책이나 지원 옵션이 다를 수 있다.

### 프로젝트 사례와 대안

- `movie_genre.genre_id`는 사용자가 직접 입력한 장르라면 NULL이 정상이다.
- 프로젝트는 표준 장르 ID만 기준으로 중복을 막지 않고, 필수 정규화 문자열을 사용한 `(movie_id, normalized_text)` UNIQUE를 둔다.
- ‘NULL을 포함한 같은 조합도 한 번만 허용’해야 한다면 모델 분리나 생성 컬럼·함수 인덱스 등 DB별 방법을 검토한다. 임의의 대체값을 사용하면 실제 값과 충돌하지 않도록 제약도 필요하다.
- 문제를 피하려고 모든 선택 컬럼을 무조건 NOT NULL로 바꾸지는 않는다.

근거: [장르 Nullable 정책](../review/database/mysql-constraint-audit.md), [MySQL UNIQUE의 NULL 처리](https://dev.mysql.com/doc/refman/8.0/en/create-index.html)

## 3. Application Validation과 DB Constraint의 역할 차이는?

### 면접 답변

“애플리케이션 검증은 요청 형식, 권한, 도메인 규칙을 확인하고 사용자에게 의미 있는 오류를 전달합니다. DB 제약은 동시 요청이나 다른 쓰기 경로에서도 저장 데이터의 유일성·필수값·참조 무결성이 깨지지 않도록 합니다. 서로 대체하는 것이 아니라 함께 사용합니다.”

| 위치 | 책임 | 프로젝트 예시 |
|---|---|---|
| DTO | 입력 형식 | 필수 제목, UUID 형식 |
| Entity | 생성·변경 규칙 | 역할별 필드 조합, 잘못된 상태 전이 거부 |
| Service | 업무 조건 | 요청자의 수정 권한, 대상 존재 확인 |
| DB | 저장 불변식 | UNIQUE, NOT NULL, CHECK, FK |

### 주의사항

- DB가 HTTP 요청자의 권한까지 자동으로 이해하는 것은 아니다.
- 애플리케이션 검사만으로 동시에 들어오는 INSERT를 완전히 통제할 수는 없다.
- Java 문자열 비교·정규화 규칙과 DB Collation의 대소문자·악센트 비교 규칙을 맞춰야 한다.
- JPA `@Column(nullable = false)`는 Java 입력 검증과 같지 않다. Flyway로 스키마를 관리하면 실제 Migration 제약도 함께 관리해야 한다.

근거: [검증 흐름 컨벤션](../convention/validation-flow-convention.md), [DB·JPA 신뢰성 컨벤션](../convention/database-jpa-reliability-convention.md)

## 4. Optimistic Lock은 언제 사용하는가?

### 면접 답변

“동시 수정 충돌이 드물고, 충돌이 발생했을 때 재조회하거나 사용자에게 알릴 수 있는 작업에 사용합니다. 읽을 때부터 배타 잠금을 유지하지 않고, 변경 시 버전이 그대로인지 확인해 다른 요청의 수정을 덮어쓰는 것을 막습니다.”

```sql
-- @Version 동작의 개념 예시
UPDATE media_encode_jobs
SET status = ?, version = version + 1
WHERE id = ? AND version = ?;
```

기대 버전과 다르면 변경되지 않고 JPA가 낙관적 락 충돌로 처리한다.

### 프로젝트 사례와 주의사항

- `MediaEncodeJob`, `MediaEncodeOutbox`, `MediaUploadRequest`, `RefreshToken`에 `@Version`을 사용한다.
- 종료 상태를 바꾸려는 잘못된 요청과 동시에 수정해서 생긴 버전 충돌을 구분한다.
- 충돌 시 항상 자동 재시도하는 것은 아니다. 이미 달라진 상태에서도 같은 요청을 실행해도 되는지 판단한다.
- ‘낙관적 락은 DB 잠금을 전혀 사용하지 않는다’고 말하지 않는다. 실제 UPDATE 중에는 InnoDB의 쓰기 잠금이 사용된다.
- 한 행의 버전만으로 여러 행에 걸친 모든 업무 규칙이 보호되는 것은 아니다.

근거: [MediaEncodeJob](../../src/main/java/com/onfilm/domain/kafka/entity/MediaEncodeJob.java), [JPA Optimistic Locking](https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html)

## 5. Pessimistic Lock은 언제 사용하는가?

### 면접 답변

“같은 데이터에 대한 경쟁이 잦거나, 검증과 변경 동안 다른 요청의 수정을 제한해야 할 때 사용합니다. 필요한 데이터를 먼저 잠그고 상태를 확인한 뒤 변경합니다. 대신 다른 요청이 대기할 수 있으므로 잠금 범위와 트랜잭션을 짧게 유지해야 합니다.”

### 프로젝트 사례

- 업로드 완료 요청에서 같은 요청 번호로 작업이 중복 생성되지 않도록 업로드 요청을 `PESSIMISTIC_WRITE`로 조회하고 상태를 확인한다.
- Outbox의 처리 대상 선점에도 비관적 락 조회를 사용한다.
- 조회 잠금과 DB UNIQUE는 역할이 다르므로 필요한 경우 함께 사용한다.

### 주의사항

- 락을 잡은 채 Kafka·S3 통신이나 인코딩을 기다리지 않도록 한다.
- `SELECT ... FOR UPDATE`는 적절한 트랜잭션 범위 안에서 사용해야 한다.
- 일반 SELECT까지 항상 차단하는 것은 아니다. MVCC와 격리 수준을 함께 봐야 한다.
- MySQL의 락 범위는 실제 사용 인덱스·조회 조건·격리 수준에 따라 레코드뿐 아니라 갭까지 포함될 수 있다.
- 아직 없는 행에 대한 중복 생성을 단순한 ‘행 락’만으로 해결했다고 단정하지 않는다.
- 데드락·잠금 대기 타임아웃과 재시도 정책도 고려한다.

근거: [MediaEncodeJobTransactionService](../../src/main/java/com/onfilm/domain/kafka/service/MediaEncodeJobTransactionService.java), [MediaUploadRequestRepository](../../src/main/java/com/onfilm/domain/kafka/repository/MediaUploadRequestRepository.java), [MediaEncodeOutboxRepository](../../src/main/java/com/onfilm/domain/kafka/repository/MediaEncodeOutboxRepository.java)

## 6. Optimistic Lock 재시도는 왜 새로운 Transaction에서 해야 하는가?

### 면접 답변

“낙관적 락 충돌이 발생한 트랜잭션은 롤백 대상으로 표시되고, 영속성 컨텍스트의 객체도 이전 버전의 상태를 가지고 있기 때문입니다. 실패한 트랜잭션을 끝내고 새 트랜잭션에서 다시 조회한 뒤, 변경된 상태에서도 요청이 유효한지 확인해야 합니다.”

### 재시도 구조

1. 트랜잭션 밖의 조정 코드가 한 번의 처리 작업을 호출한다.
2. 각 시도는 별도 Bean의 트랜잭션 메서드 또는 TransactionTemplate의 독립 경계에서 실행한다.
3. flush·commit에서 발생한 충돌까지 바깥에서 받는다.
4. 재시도 가능한 작업인지 확인하고, 필요한 경우 제한된 횟수와 대기 정책으로 다시 시도한다.
5. 새 시도에서는 DB 재조회와 업무 조건 검증부터 수행한다.

### 주의사항

- 같은 `@Transactional` 메서드 안에서 catch 후 반복하거나 clear만 호출하는 것으로 복구되지 않는다.
- 같은 클래스의 내부 호출은 기본 Spring 프록시를 거치지 않아 새 트랜잭션이 적용되지 않을 수 있다.
- 바깥에 트랜잭션이 없다면 각 시도에 REQUIRED를 사용해도 새 트랜잭션이 생긴다. 무조건 REQUIRES_NEW만 필요한 것은 아니다.
- 바깥 트랜잭션에 참여하지 않도록 경계를 확인하고, 새 트랜잭션에 오래된 엔티티 인스턴스를 재사용하지 않는다.
- S3 업로드·메시지 발행까지 반복하면 부작용이 중복될 수 있어 멱등성도 고려한다.
- 이것은 일반 재시도 설계 원칙이다. 프로젝트의 모든 낙관적 락 충돌을 자동 재시도한다고 주장하지 않는다.

공식 참고: [JPA OptimisticLockException·Rollback](https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html), [Spring 트랜잭션 전파](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html)

## 7. Index는 왜 사용하는가?

### 면접 답변

“조건에 맞는 데이터를 찾기 위해 읽어야 하는 범위를 줄이고, 경우에 따라 정렬이나 집계 비용도 줄이기 위해 사용합니다. 일반적인 InnoDB 인덱스는 정렬된 B+Tree 구조로 특정 값이나 범위를 빠르게 찾도록 돕습니다.”

### 프로젝트 사례

완료 Job 정리 대상 ID 조회에 `(status, completed_at)` 인덱스를 적용했다. 20만 건 기준으로 전체 테이블 탐색에서 필요한 범위의 인덱스 탐색으로 바뀌었고, 동일 조건 3회 측정 중앙값이 123ms에서 3.40ms로 줄었다.

### 비용과 주의사항

- INSERT·DELETE와 인덱스 컬럼 UPDATE 시 인덱스도 관리해야 한다.
- 저장 공간, 버퍼 메모리, 페이지 분할과 쓰기 비용이 추가된다.
- 인덱스가 있다고 Optimizer가 반드시 사용하는 것은 아니다.
- 위 수치는 SELECT 실행 시간이다. 실제 삭제 처리나 API 전체 응답 시간이 아니다.
- 해당 실험은 VISIBLE·INVISIBLE로 조회 경로를 비교했으므로 인덱스 추가에 따른 쓰기 비용까지 측정한 것은 아니다.

근거: [인덱스 전후 비교](../performance/mysql-index-before-after-comparison.md), [V5 Migration](../../src/main/resources/db/migration/V5__add_media_maintenance_indexes.sql), [MySQL 인덱스 사용](https://dev.mysql.com/doc/refman/8.0/en/mysql-indexes.html)

## 8. 선택도는 무엇인가?

### 면접 답변

“조회 조건이나 인덱스가 전체 데이터에서 후보를 얼마나 좁힐 수 있는지 판단하는 개념입니다. 적은 행만 찾아도 되는 조건일수록 인덱스 탐색이 유리한 경우가 많습니다. 다만 선택도라는 용어는 자료마다 계산 기준이 달라, 저는 어떤 비율을 말하는지 함께 설명합니다.”

### 혼동하기 쉬운 두 기준

| 기준 | 계산·의미 | 해석 |
|---|---|---|
| 인덱스 값의 구분 정도 | 서로 다른 값 수 / 전체 행 수 | 비율이 높을수록 평균적으로 같은 값에 묶이는 행이 적음 |
| 특정 조건의 통과 비율 | 조건에 맞는 행 수 / 전체 행 수 | 비율이 낮을수록 그 조건이 많은 행을 걸러냄 |

- Cardinality는 서로 다른 값의 개수다. `SHOW INDEX`의 Cardinality는 통계에 기반한 추정치다.
- 10만 건 중 이메일 하나로 1건을 찾는 경우는 조건에 맞는 행이 매우 적다.
- 상태가 두 종류뿐이어도 FAILED가 10건밖에 없다면 `status = 'FAILED'` 조건은 많은 행을 걸러낸다. 값 종류만 보고 인덱스가 무의미하다고 결론내리지 않는다.
- 값 분포의 편중, 통계 정확도, 정렬·LIMIT·Covering 여부도 함께 확인한다.

### 프로젝트 연결

`status`는 값 종류가 적지만 시간 범위와 결합한 `(status, completed_at)` 인덱스로 실제 정리 대상 범위를 줄였다. ‘선택도가 높은 컬럼을 항상 맨 앞에 둔다’는 규칙만으로 설계하지 않았다.

근거: [실제 비교 기록](../performance/mysql-index-before-after-comparison.md), [MySQL 인덱스 선택](https://dev.mysql.com/doc/refman/8.0/en/mysql-indexes.html)

## 9. Composite Index는 언제 사용하는가?

### 면접 답변

“여러 컬럼의 조건과 정렬을 함께 사용하는 주요 쿼리를 지원할 때 사용합니다. 컬럼을 지정한 순서대로 정렬하므로, 어떤 조건으로 먼저 범위를 좁히고 그 안에서 어떤 범위를 읽거나 정렬할지에 맞춰 순서를 정합니다.”

### 프로젝트 예시

```sql
SELECT j.id
FROM media_encode_jobs j
WHERE j.status IN ('DONE', 'FAILED')
  AND j.completed_at < '2026-01-08 00:00:00';

-- 실제 V5 인덱스 구조
-- (status, completed_at)
```

상태별 범위 안에서 완료 시각 기준으로 필요한 구간을 탐색하도록 설계했다.

### 컬럼 순서와 주의사항

- `(a, b, c)`는 일반적으로 `(a)`, `(a, b)`, `(a, b, c)` 같은 왼쪽 선두 컬럼 조합의 검색에 유리하다.
- `b`만 조건에 있으면 선두 컬럼을 이용한 일반적인 탐색과 같지 않다. 그렇다고 인덱스 스캔이나 Skip Scan 등 모든 활용이 불가능하다고 단정하지 않는다.
- 동등 조건 다음에 범위 조건을 두는 것은 유용한 출발점이지만 절대 규칙은 아니다. 정렬, IN 조건, 데이터 분포, LIMIT를 함께 본다.
- 범위 조건 뒤 컬럼이 탐색 범위를 크게 줄이지 못해도 필터링·Covering 등에 쓰일 수 있다.
- WHERE에 조건을 적은 순서를 바꾸는 것과 인덱스 컬럼 순서를 바꾸는 것은 다르다.
- 단일 인덱스 여러 개가 복합 인덱스와 항상 같은 효과를 내지는 않는다.
- Outbox의 lease 인덱스 후보는 실행 계획에서 효과를 확인하지 못해 V5에서 제외했다.

근거: [V5 Migration](../../src/main/resources/db/migration/V5__add_media_maintenance_indexes.sql), [인덱스 전후 비교](../performance/mysql-index-before-after-comparison.md), [MySQL 복합 인덱스](https://dev.mysql.com/doc/refman/8.0/en/multiple-column-indexes.html)

## 10. Covering Index는 무엇인가?

### 면접 답변

“쿼리에 필요한 컬럼을 인덱스가 모두 포함해, 일반적으로 별도의 테이블 행 조회 없이 인덱스만으로 결과를 얻을 수 있는 경우를 말합니다. 특정한 이름의 인덱스 종류라기보다 인덱스와 쿼리의 관계입니다.”

### 프로젝트 사례

7·9번 쿼리는 `status`, `completed_at`으로 조건을 검사하고 `id`만 반환한다. InnoDB 보조 인덱스에는 PK도 포함되므로 `(status, completed_at)` 인덱스로 이 쿼리를 커버할 수 있다. 실제 EXPLAIN ANALYZE에서도 covering range scan을 확인했다.

### 주의사항

- `SELECT *`처럼 다른 컬럼까지 요구하면 같은 인덱스가 더 이상 커버하지 못할 수 있다.
- 인덱스에 반환 컬럼을 무조건 늘리면 인덱스 크기와 쓰기 비용이 증가한다.
- 전통적인 EXPLAIN의 `Using index`는 인덱스만으로 필요한 컬럼을 처리하는 계획을 뜻한다.
- `Using index condition`은 인덱스 조건 푸시다운으로, Covering과 같지 않다.
- MVCC 가시성 확인이나 잠금 조회 등 실행 조건에 따라 클러스터 인덱스 접근이 필요할 수 있으므로 물리적 행 접근이 항상 0이라고 단정하지 않는다.

근거: [실제 Covering 측정](../performance/mysql-index-before-after-comparison.md), [MySQL EXPLAIN Extra](https://dev.mysql.com/doc/refman/8.0/en/explain-output.html)

## 11. EXPLAIN에서 어떤 항목을 확인하는가?

### 면접 답변

“어떤 순서와 방법으로 테이블을 읽는지, 실제 선택한 인덱스가 무엇인지, 예상 탐색 행과 필터링 정도를 확인합니다. 추가 정렬이나 임시 테이블 사용도 보고, 필요하면 EXPLAIN ANALYZE로 예상과 실제 처리량·시간의 차이를 비교합니다.”

| 항목 | 확인할 내용 |
|---|---|
| `id`, `select_type`, `table` | 쿼리 블록과 대상 테이블, 조인 구조 |
| `type` | ALL·index·range·ref·eq_ref·const 등 접근 방식 |
| `possible_keys` | 사용 후보 인덱스 |
| `key` | 실제 선택한 인덱스 |
| `key_len` | 사용한 키 길이. 스키마와 함께 인덱스 사용 범위를 해석 |
| `ref` | 인덱스 조회에서 비교하는 컬럼·상수 |
| `rows` | 읽을 것으로 예상하는 행 수 |
| `filtered` | 조건 통과 행의 예상 비율 |
| `Extra` | Using index, Using index condition, Using filesort, Using temporary 등 |

### 해석 시 주의사항

- `rows`는 실제 측정값이 아니다. `rows × filtered / 100`도 해당 단계의 출력 규모 추정이지 전체 쿼리 비용 공식은 아니다.
- `type = index`도 인덱스 전체 스캔일 수 있어 ‘인덱스를 썼으니 빠르다’고 판단하지 않는다.
- `Using filesort`는 인덱스 순서로 해결하지 못한 별도 정렬을 뜻하며 반드시 디스크 정렬을 뜻하지 않는다.
- `Using temporary`도 무조건 디스크 사용을 뜻하지 않는다.
- 인덱스만 커버하는 계획은 `possible_keys`와 `key`의 관계가 단순하지 않을 수 있다. 하나의 항목만 보고 결론내리지 않는다.

### EXPLAIN ANALYZE

- 실제로 쿼리를 실행한다. 데이터가 많으면 부하가 생기므로 전용 검증 환경에서 수행한다.
- `actual time`, `rows`, `loops`를 확인한다. 반복되는 노드는 회당 출력과 반복 횟수를 함께 해석한다.
- 노드별 시간이 상위·하위 작업을 포함할 수 있어 모두 더해 전체 시간이라고 계산하지 않는다.
- MySQL 버전·출력 형식에 따라 표시 항목이 다르다.
- 프로젝트에서는 같은 데이터·쿼리·cutoff로 워밍업 후 3회 측정하고 중앙값을 비교했다.

근거: [MySQL EXPLAIN 출력](https://dev.mysql.com/doc/refman/8.0/en/explain-output.html), [프로젝트 측정 방법](../performance/mysql-index-before-after-comparison.md)

## 12. Full Table Scan은 항상 나쁜가?

### 면접 답변

“아닙니다. 테이블이 작거나 대부분의 행을 읽어야 한다면 인덱스를 탐색하고 테이블 행을 반복 조회하는 것보다 전체를 읽는 편이 효율적일 수 있습니다. 반환할 데이터에 비해 얼마나 불필요한 탐색을 하는지와 실제 비용으로 판단해야 합니다.”

### 판단 기준

- 테이블 크기와 결과 행 비율
- 행 폭, 캐시 상태, 인덱스에서 테이블로 돌아가는 조회 비용
- Covering·정렬·LIMIT 활용 여부
- 쿼리 실행 빈도와 실제 지연 시간

### 프로젝트 사례와 주의사항

- 완료 Job 정리 대상은 20만 건 중 7,056건이었다. 전체 탐색을 필요한 인덱스 범위 조회로 바꿔 실제 효과를 확인했다.
- 결과가 테이블의 몇 % 이상이면 무조건 Full Scan이라는 고정 기준으로 외우지 않는다.
- 예상과 달리 전체 탐색한다면 통계, 데이터 편중, 함수·형 변환, 조건과 인덱스의 불일치 등을 확인한다.
- `FORCE INDEX`부터 적용하기보다 실행 계획의 선택 이유와 실제 측정을 먼저 확인한다.

근거: [MySQL Full Table Scan 설명](https://dev.mysql.com/doc/refman/8.0/en/table-scan-avoidance.html), [프로젝트 비교 기록](../performance/mysql-index-before-after-comparison.md)

## 13. H2 테스트가 MySQL 운영 환경을 완전히 대체할 수 없는 이유는?

### 면접 답변

“H2의 MySQL 호환 모드는 일부 문법과 동작을 맞춰주는 기능이지 MySQL 엔진 자체가 되는 것은 아닙니다. 타입과 Collation, 제약조건, 잠금·격리 수준, 실행 계획이 달라 H2에서 통과한 테스트가 MySQL에서도 같다고 보장할 수 없습니다.”

### 프로젝트에서 검증한 범위

- Testcontainers MySQL에서 Flyway Migration을 적용하고 Hibernate validate를 실행한다.
- Repository의 연관관계 저장·삭제와 Unique·Not Null·FK 위반을 확인한다.
- 독립된 트랜잭션과 연결을 사용해 동시 요청·잠금·버전 충돌을 검증한다.
- 실제 commit 후 재조회, 실패 시 rollback, REQUIRES_NEW 기록 보존을 구분한다.
- 순서 컬럼은 Hibernate가 INSERT 후 UPDATE하는 동작을 MySQL에서 확인하고 제약 정책에 반영했다.

### 주의사항

- H2가 UNIQUE나 트랜잭션을 지원하지 않는다는 뜻은 아니다. 구현체·설정별 차이가 문제다.
- 테스트의 자동 롤백만 확인하고 실제 커밋·AFTER_COMMIT을 검증했다고 말하지 않는다.
- Testcontainers를 사용해도 운영과 완전히 같은 환경이 되는 것은 아니다. DB 버전, 문자셋·Collation, SQL mode, 격리 수준, 타임존 등을 맞춰야 한다.
- RDS 자원·네트워크 지연·장애 조치·운영 데이터 분포는 별도 환경에서 검증한다.
- 빠른 단위 테스트와 H2 테스트를 유지하면서, MySQL 의존성이 큰 영역을 실제 MySQL 통합 테스트로 보완한다.

근거: [H2 호환 모드](https://h2database.com/html/features.html#compatibility), [DB·JPA 신뢰성 개선 사례](../problem-solving/11-database-jpa-reliability.md), [MySQL Testcontainers 환경](../testing/mysql-testcontainers.md)

## 14. 트랜잭션의 ACID는 무엇이며 프로젝트에서 어떻게 설명할 수 있는가?

### 면접 답변

“원자성은 트랜잭션의 변경이 전부 반영되거나 취소되는 성질이고, 일관성은 정의한 무결성 규칙을 유지하는 것입니다. 격리성은 동시에 실행되는 트랜잭션 간 간섭을 제어하고, 지속성은 성공적으로 커밋한 결과를 보존하는 성질입니다.”

### 프로젝트 연결과 주의사항

- Job과 Outbox를 같은 DB 트랜잭션으로 저장한 것이 원자성의 사례다.
- UNIQUE·FK와 상태 검증은 일관성을 지키는 장치다. DB가 모든 비즈니스 규칙을 자동으로 알아서 지킨다는 뜻은 아니다.
- 격리 수준·락·버전 검증을 조합해 동시 변경을 다룬다. `@Transactional`만 붙이면 모든 경쟁 조건이 해결되는 것은 아니다.
- 지속성은 스토리지·로그 동기화 설정과 장애 종류를 함께 고려한다. 복제·백업·고가용성과 같은 개념은 아니다.
- 로컬 DB 트랜잭션이 Kafka 발행이나 S3 변경까지 자동으로 포함하지 않는다.

근거: [DB·JPA 신뢰성 사례](../problem-solving/11-database-jpa-reliability.md), [트랜잭션 경계 사례](../problem-solving/09-transaction-boundary-and-external-io.md)

## 15. 트랜잭션 격리 수준은 어떻게 구분하는가?

### 면접 답변

“다른 트랜잭션의 변경을 어느 시점까지 볼 수 있는지와 동시에 수행되는 작업을 얼마나 분리할지를 정하는 기준입니다. READ UNCOMMITTED, READ COMMITTED, REPEATABLE READ, SERIALIZABLE이 있고, 수준을 높일수록 일반적으로 동시 처리에 제약과 비용이 추가됩니다.”

| 수준 | 주요 의미 |
|---|---|
| READ UNCOMMITTED | 다른 트랜잭션의 미커밋 변경을 읽을 수 있음 |
| READ COMMITTED | 커밋된 데이터만 읽고, 같은 트랜잭션의 반복 조회 결과는 달라질 수 있음 |
| REPEATABLE READ | 반복 읽기의 일관성을 강화함. 구체적 구현은 DB별로 다름 |
| SERIALIZABLE | 동시 실행 결과를 직렬 실행과 동등하게 만들도록 가장 강하게 격리 |

### 주의사항

- Dirty Read는 미커밋 값 읽기, Non-repeatable Read는 같은 행 재조회 시 값 변경, Phantom Read는 같은 조건 재조회 시 행 집합 변경이다.
- SQL 표준에서 허용하는 현상과 MySQL InnoDB 구현을 구분한다. 16~17번에서 구체화한다.
- InnoDB 기본값은 REPEATABLE READ이지만 실제 애플리케이션 연결의 설정을 확인해야 한다.
- SERIALIZABLE도 처리 대기·데드락·재시도 문제까지 없애지는 않는다.
- 은행 업무라고 무조건 모든 트랜잭션을 SERIALIZABLE로 지정하는 것은 아니다. 업무 불변식과 실제 경쟁을 기준으로 선택한다.

공식 참고: [MySQL 격리 수준](https://dev.mysql.com/doc/refman/8.4/en/innodb-transaction-isolation-levels.html)

## 16. MVCC는 무엇이며 READ COMMITTED와 REPEATABLE READ에서 어떻게 다른가?

### 면접 답변

“MVCC는 데이터의 여러 버전과 가시성 정보를 이용해, 읽기와 쓰기가 서로를 무조건 기다리지 않도록 하는 방식입니다. InnoDB의 일반적인 일관 읽기에서는 undo 정보를 이용해 해당 스냅샷에서 볼 수 있는 값을 읽습니다.”

### 스냅샷 차이

- READ COMMITTED: 각 일관 읽기마다 새로운 스냅샷을 사용한다.
- REPEATABLE READ: 보통 첫 일관 읽기에서 정한 스냅샷을 같은 트랜잭션의 이후 일관 읽기에서도 사용한다.
- `START TRANSACTION WITH CONSISTENT SNAPSHOT` 등 스냅샷 생성 시점을 명시하는 경우는 구분한다.
- 자신의 트랜잭션에서 변경한 내용은 볼 수 있으므로 ‘무조건 시작 당시 값만 본다’는 설명은 부정확하다.
- `SELECT ... FOR UPDATE`, UPDATE 같은 잠금 읽기·변경은 일반 스냅샷 SELECT와 같은 방식으로만 동작하지 않는다.

### 비용과 주의사항

- 오래 열린 읽기 트랜잭션이 과거 버전을 필요로 하면 undo 정리가 지연될 수 있다.
- MVCC가 있으므로 락이 필요 없다는 뜻이 아니다. 쓰기 경쟁에는 별도 잠금이 사용된다.
- DB 스냅샷과 JPA 1차 캐시는 다른 계층이다. 조회 결과를 비교할 때 어느 계층의 값인지 확인한다.

공식 참고: [InnoDB Consistent Reads](https://dev.mysql.com/doc/refman/8.4/en/innodb-consistent-read.html)

## 17. MySQL REPEATABLE READ에서는 Phantom Read가 발생하지 않는가?

### 면접 답변

“일반 SELECT의 반복 일관 읽기는 같은 스냅샷을 사용하므로 다른 트랜잭션이 추가한 행이 갑자기 보이지 않게 합니다. 반면 잠금 읽기는 next-key lock 등으로 검색 범위의 삽입을 제어할 수 있습니다. 두 방식의 동작을 구분해서 설명해야 합니다.”

### 주의사항

- ‘REPEATABLE READ는 팬텀을 허용한다’는 표준 수준의 설명만으로 InnoDB의 모든 실행을 설명하지 않는다.
- 반대로 ‘MySQL RR에서는 어떤 조회를 섞어도 팬텀이나 동시성 문제가 전혀 없다’고 말하지 않는다.
- 스냅샷 SELECT와 `FOR UPDATE`를 섞으면 참조하는 데이터 상태가 달라질 수 있다.
- 유일 인덱스의 정확한 한 행 조회와 범위 조회는 잠금 범위가 다를 수 있다.
- 개수 제한처럼 여러 행에 걸친 규칙은 어떤 공통 자원을 잠그거나 어떤 원자적 조건으로 보호할지 별도로 설계한다.

공식 참고: [InnoDB 격리 수준](https://dev.mysql.com/doc/refman/8.4/en/innodb-transaction-isolation-levels.html), [InnoDB Locking](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking.html)

## 18. Lost Update는 무엇이며 원자적 UPDATE로 어떻게 줄일 수 있는가?

### 면접 답변

“두 요청이 같은 값을 읽고 각각 계산한 값을 저장하면서 한쪽 변경이 덮어써지는 문제입니다. 버전 검사나 비관적 락을 사용할 수 있고, 단순 증감이라면 DB에서 조건 검사와 변경을 하나의 UPDATE로 수행하는 방법도 있습니다.”

```sql
-- 일반적인 재고 예시이며 OnFilm 적용 사례가 아니다.
UPDATE stock
SET quantity = quantity - 1
WHERE product_id = :id
  AND quantity >= 1;
```

- 조건에 맞아 변경된 행이 1건인지 확인해 성공 여부를 판단한다.
- 애플리케이션에서 수량 10을 읽어 9를 대입하는 방식과, DB에서 현재 수량을 1 줄이는 방식은 다르다.
- 원자적 UPDATE도 같은 행에 대한 DB 락과 대기는 발생할 수 있다.
- 같은 요청이 재전송되면 수량을 두 번 줄일 수 있으므로 멱등성 키·처리 기록도 필요하다.
- 여러 테이블의 작업이면 해당 변경을 포함한 트랜잭션 전체의 원자성도 설계해야 한다.

## 19. Record Lock, Gap Lock, Next-key Lock은 어떻게 다른가?

### 면접 답변

“Record Lock은 인덱스 레코드를 잠그고, Gap Lock은 인덱스 값 사이의 구간에 삽입하는 것을 제한합니다. Next-key Lock은 레코드와 그 앞의 갭을 함께 잠그는 방식입니다. InnoDB에서 범위 조회의 동시 삽입을 제어할 때 중요한 개념입니다.”

### 예시와 주의사항

- 인덱스 값이 10, 20이라면 `(10, 20)` 구간의 삽입을 제한하는 갭 잠금과 20 레코드 자체의 잠금은 다르다.
- Next-key Lock은 개념적으로 `(10, 20]`처럼 앞 구간과 레코드를 포함한다.
- UNIQUE 키의 모든 컬럼으로 존재하는 한 행을 정확히 찾는 경우에는 일반적으로 갭 잠금이 필요하지 않다. 없는 키 검색은 별도로 봐야 한다.
- 격리 수준·조건·인덱스에 따라 적용 방식이 달라진다. READ COMMITTED에서는 일반 검색의 갭 잠금이 줄지만 FK·중복 검사 등의 예외가 있다.
- InnoDB가 ‘행 단위 락’이라고 해서 항상 WHERE 결과 행만 잠그는 것은 아니다.

공식 참고: [InnoDB Locking](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking.html)

## 20. Deadlock과 Lock Wait Timeout은 어떻게 다른가? 어떻게 대응하는가?

### 면접 답변

“Deadlock은 여러 트랜잭션이 서로 가진 잠금을 기다리는 순환 관계이고, Lock Wait Timeout은 잠금을 정해진 시간 안에 얻지 못한 상황입니다. 타임아웃이 발생했다고 반드시 데드락이 있었다는 뜻은 아닙니다.”

### 대응

- 여러 자원을 변경할 때 접근 순서를 통일한다.
- 트랜잭션을 짧게 유지하고 필요한 인덱스로 잠금 탐색 범위를 줄인다.
- 데드락 기록과 실제 SQL·잠금 순서를 확인한다. MySQL에서는 `SHOW ENGINE INNODB STATUS`와 Performance Schema의 잠금 정보를 활용할 수 있다.
- 재시도 가능한 업무라면 실패한 시도를 끝내고 새 트랜잭션에서 제한적으로 재시도한다.
- DB 커넥션을 잡은 채 긴 backoff 대기를 하지 않는다.

### 롤백 범위의 차이

- InnoDB가 데드락을 감지하면 희생 트랜잭션 전체를 롤백한다.
- InnoDB 잠금 대기 타임아웃은 기본적으로 실패한 SQL 문장만 롤백하며, 설정에 따라 전체 롤백이 가능하다.
- Spring/JPA 계층이 예외를 처리하며 전체 트랜잭션을 롤백할 수도 있으므로 DB 동작과 애플리케이션 정책을 구분한다.
- 타임아웃 이후 나머지 작업을 무조건 커밋하면 업무 원자성이 깨질 수 있다.

공식 참고: [MySQL 데드락 대응](https://dev.mysql.com/doc/refman/8.4/en/innodb-deadlocks-handling.html), [InnoDB 오류 처리](https://dev.mysql.com/doc/refman/8.4/en/innodb-error-handling.html)

## 21. SELECT FOR UPDATE의 NOWAIT와 SKIP LOCKED는 언제 사용하는가?

### 면접 답변

“NOWAIT는 필요한 행이 잠겨 있으면 기다리지 않고 오류를 반환하고, SKIP LOCKED는 잠긴 행을 건너뛰어 다른 행을 가져옵니다. SKIP LOCKED는 여러 처리자가 작업 테이블에서 서로 다른 작업을 가져갈 때 유용합니다.”

### 주의사항

- 일부 행을 건너뛰므로 전체 결과의 일관성이 필요한 금융 조회 등에 무조건 사용하지 않는다.
- 작업 순서가 엄격한 FIFO로 유지된다는 보장은 없다. 오래 잠긴 작업의 지연도 관찰해야 한다.
- 이미 잠긴 행을 피하는 기능이지, 트랜잭션 종료 후의 처리 소유권까지 보장하는 기능은 아니다.
- MySQL의 해당 옵션은 행 수준 잠금에 관한 것이므로 모든 종류의 대기를 없앤다고 설명하지 않는다.
- 현재 확인한 API Outbox Repository는 `PESSIMISTIC_WRITE`를 사용하지만 SKIP LOCKED를 명시하지 않는다. 적용 사례가 아닌 확장 대안으로 설명한다.

근거: [MediaEncodeOutboxRepository](../../src/main/java/com/onfilm/domain/kafka/repository/MediaEncodeOutboxRepository.java), [MySQL Locking Reads](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html)

## 22. 인덱스가 동시성과 잠금 범위에도 영향을 주는가?

### 면접 답변

“그렇습니다. InnoDB는 인덱스를 탐색하며 필요한 레코드나 범위를 잠그므로, 조건에 맞는 인덱스가 없으면 많은 레코드를 탐색하고 잠그게 될 수 있습니다. 인덱스는 조회 시간뿐 아니라 다른 요청이 기다리는 범위와 시간에도 영향을 줍니다.”

### 주의사항

- WHERE에서 최종적으로 반환되는 행 수와 잠금이 걸리는 탐색 범위가 항상 같지는 않다.
- 인덱스가 없으면 무조건 하나의 테이블 락으로 바뀐다고 말하지 않는다. 많은 인덱스 레코드의 잠금이 문제일 수 있다.
- 보조 인덱스를 통한 배타 잠금은 대응하는 클러스터 인덱스 레코드에도 영향을 줄 수 있다.
- 인덱스를 추가하면 쓰기 시 관리할 인덱스도 늘어난다. 동시성이 항상 좋아진다고 단정하지 않는다.
- 조회용 EXPLAIN 외에 실제 경쟁 요청의 대기·데드락·처리 시간도 함께 확인한다.

공식 참고: [SQL별 InnoDB 잠금](https://dev.mysql.com/doc/refman/8.4/en/innodb-locks-set.html)

## 23. Clustered Index와 Secondary Index는 무엇이며 PK 선택은 왜 중요한가?

### 면접 답변

“InnoDB는 일반적으로 PK를 클러스터 인덱스로 사용하고 그 리프에 행 데이터를 저장합니다. 보조 인덱스에는 인덱스 키와 PK가 들어 있으므로, 필요한 다른 컬럼을 읽으려면 PK로 클러스터 인덱스를 추가 탐색할 수 있습니다.”

### PK 선택의 영향

- 긴 PK는 보조 인덱스에도 포함돼 저장 공간과 캐시 효율에 영향을 준다.
- 증가하는 키는 삽입 위치의 지역성이 좋고, 무작위 키는 페이지 분할과 캐시 사용 측면에서 비용이 커질 수 있다.
- 하지만 UUID는 시스템 간 식별자 생성·전달에 이점이 있다. 성능만으로 모든 ID를 같은 방식으로 바꾸지 않는다.
- PK가 없으면 InnoDB는 적합한 NOT NULL UNIQUE 키 등을 찾고, 없으면 내부 식별자를 사용한다.
- 클러스터 인덱스의 키 순서와 디스크 파일의 모든 페이지가 물리적으로 연속 배치된다는 주장은 구분한다.

### 프로젝트 연결

프로젝트에는 숫자 ID를 사용하는 업무 엔티티와 문자열 UUID를 사용하는 Job·Outbox가 함께 존재한다. 보조 인덱스 크기 측정 시 PK 크기의 영향도 고려할 수 있지만, 실제 측정 없이 UUID 변경 효과를 성과로 주장하지 않는다.

공식 참고: [InnoDB Clustered and Secondary Indexes](https://dev.mysql.com/doc/refman/8.4/en/innodb-index-types.html)

## 24. OFFSET 페이징이 뒤로 갈수록 느려질 수 있는 이유는? Keyset Pagination은 무엇인가?

### 면접 답변

“OFFSET은 결과를 만들면서 앞의 많은 행을 읽고 건너뛰어야 할 수 있어 뒷페이지 비용이 커집니다. Keyset Pagination은 마지막으로 본 정렬 키 다음부터 조회해 큰 OFFSET을 피하는 방식입니다.”

```sql
-- 일반 개념 예시: created_at과 id는 NOT NULL이라고 가정한다.
SELECT id, created_at
FROM items
WHERE created_at < :lastCreatedAt
   OR (created_at = :lastCreatedAt AND id < :lastId)
ORDER BY created_at DESC, id DESC
LIMIT 20;
```

### 주의사항

- 시간이 같은 행을 구분하도록 유일한 ID를 정렬 기준에 포함한다.
- 조건과 정렬을 지원하는 인덱스 및 실제 실행 계획이 필요하다. 문법만 바꿨다고 빨라지는 것은 아니다.
- 임의의 100페이지로 바로 이동하는 기능이나 정확한 총 페이지 수 제공에는 불편할 수 있다.
- 정렬 키가 수정되면 중복·누락 가능성이 남는다. 안정적인 키와 조회 일관성 정책을 정한다.
- 프로젝트에 이 방식이 적용됐다고 주장하는 답변이 아니라 페이징 대안을 설명하는 예시다.

## 25. 인덱스를 만들었는데 사용하지 않는다면 무엇을 확인하는가?

### 면접 답변

“실제 SQL과 파라미터부터 확인하고, 조건·정렬이 인덱스의 컬럼 순서와 맞는지 봅니다. 그다음 데이터 분포와 통계, 함수나 형 변환, 반환할 행의 비율을 확인합니다. Optimizer가 전체 탐색을 더 저렴하다고 판단했을 수도 있어 실제 실행 시간으로 비교합니다.”

### 점검 항목

- 선두 컬럼 없이 뒤 컬럼만 검색하는지
- 인덱스 컬럼에 함수·암시적 형 변환이 적용되는지
- `LIKE '%검색어%'`처럼 일반 B-Tree 범위 탐색에 불리한 조건인지
- 실제 값이 대부분의 행에 해당하는지
- 통계가 데이터 분포를 잘 나타내는지, `ANALYZE TABLE` 등 갱신이 필요한지
- OR·IN·정렬·LIMIT 때문에 예상과 다른 계획이 선택됐는지
- 인덱스가 INVISIBLE인지, 실제 배포 DB에 해당 Migration이 적용됐는지

### 프로젝트 연결

Outbox claim의 OR 조건에서 lease 인덱스 후보가 선택되지 않아 V5에서는 제외했다. 무조건 FORCE INDEX를 사용하지 않고, 필요하면 조회를 분리한 뒤 다시 측정하는 후속 방향을 기록했다.

근거: [인덱스 전후 비교의 변경하지 않은 SQL](../performance/mysql-index-before-after-comparison.md)

## 26. Collation이 UNIQUE와 로그인 식별자에 영향을 주는 이유는?

### 면접 답변

“Collation은 문자열의 비교와 정렬 규칙이므로, DB에서 어떤 문자열을 같은 값으로 판단하는지에 영향을 줍니다. Java에서는 다르게 보는 문자열을 DB에서는 대소문자나 악센트 차이를 무시해 같다고 볼 수 있어, 애플리케이션 정규화와 DB 비교 정책을 맞춰야 합니다.”

### 프로젝트 연결과 주의사항

- 기본 `utf8mb4_0900_ai_ci`는 악센트와 대소문자를 구분하지 않는 규칙이다.
- 사용자 검색용 문자열과 토큰 해시처럼 정확한 비교가 필요한 문자열은 같은 기준을 적용하지 않는다.
- 프로젝트에서는 토큰 해시의 정확 비교 정책을 DB 제약 검사와 Migration에 반영했다.
- Unicode 정규화, 공백 처리, 길이 검증까지 Collation 하나가 대신하지 않는다.
- Collation 변경 전에는 새 규칙에서 중복이 되는 기존 데이터가 있는지 검사해야 한다.

근거: [Constraint 검사 기록의 Collation 정책](../review/database/mysql-constraint-audit.md), [V3 Migration](../../src/main/resources/db/migration/V3__strengthen_refresh_token_constraints.sql)

## 27. FK는 모든 참조 컬럼에 추가해야 하는가? ON DELETE 정책은 어떻게 정하는가?

### 면접 답변

“항상 추가하는 것이 아니라 참조 대상이 없어졌을 때 해당 데이터가 남아도 되는지와 생명주기를 먼저 정합니다. 강한 참조 관계에는 FK로 무결성을 보장하고, 이력을 독립적으로 보존해야 하는 참조는 삭제 정책과 보존 기간을 검토합니다.”

### 프로젝트 사례

- Movie가 삭제되면 그 작품의 참여 관계도 제거한다.
- Person이 삭제되면 해당 Person의 참여 관계만 제거하고 Movie와 다른 참여자의 이력은 유지한다.
- Job·Outbox는 원본 Movie·User와 보존 기간이 달라 일부 ID를 작업 시점 정보로 유지하고 FK를 두지 않는다.
- RefreshToken은 JPA 객체 연관관계를 두지 않아도 사용자 삭제에 따른 정리를 보장하도록 DB FK를 둔다.

### 주의사항

- CASCADE는 함께 삭제, RESTRICT는 참조 중인 부모 삭제 차단, SET NULL은 참조를 비우는 정책이다. SET NULL에는 컬럼의 NULL 허용이 필요하다.
- JPA 연관관계의 유무와 DB FK의 유무는 별개의 결정이다.
- FK가 없는 관계의 존재·소유권·보존 정책은 애플리케이션과 운영 절차로 책임져야 한다.
- 논리 DB 간 FK를 만들지 않는 것은 프로젝트 소유권 정책이지 MySQL의 일반적인 기술적 불가능을 뜻하지 않는다.

근거: [Constraint 검사 기록](../review/database/mysql-constraint-audit.md)

## 28. 순서 컬럼에 NOT NULL이나 UNIQUE를 바로 추가하지 않은 이유는?

### 면접 답변

“최종 상태만 보면 적절한 제약이어도 JPA가 SQL을 실행하는 중간 단계에서 충돌할 수 있기 때문입니다. 프로젝트의 OrderColumn은 자식을 먼저 INSERT한 뒤 순서를 UPDATE하는 동작을 MySQL에서 확인해 일부 컬럼을 nullable로 유지했습니다.”

### 순서 교환 예시와 검증

- 순서 0과 1을 교환할 때 첫 번째 행을 1로 바꾸면, 아직 1인 두 번째 행과 일시적으로 충돌할 수 있다.
- MySQL UNIQUE 검사는 커밋까지 자동으로 지연되지 않으므로 최종 순서가 유일하다는 사실만으로 충분하지 않다.
- 프로젝트는 재정렬 요청의 정확한 순열을 엔티티에서 검증하고, 음수 순서는 CHECK로 차단하며, commit 후 순서 저장을 통합 테스트로 확인한다.
- `CHECK(sort_order >= 0)`는 NULL 차단을 대신하지 않는다. SQL CHECK는 UNKNOWN 결과를 허용하므로 NULL 금지는 별도의 NOT NULL 정책이 필요하다.
- 더 강한 DB 순서 제약이 필요하면 임시 순서 부여나 순서 모델 변경 등 저장 알고리즘까지 함께 설계해야 한다.

근거: [OrderColumn·Nullable 검사 결과](../review/database/mysql-constraint-audit.md), [V4 Migration](../../src/main/resources/db/migration/V4__strengthen_aggregate_constraints.sql)

## 29. Flyway와 Hibernate validate를 함께 사용한 이유는? Migration 실패는 모두 롤백되는가?

### 면접 답변

“Flyway는 스키마 변경의 순서와 이력을 관리하고, Hibernate validate는 엔티티가 기대하는 매핑과 실제 스키마의 불일치를 확인하는 데 사용했습니다. 변경을 SQL로 검토·재현하면서 애플리케이션 매핑도 확인하기 위해 역할을 나눴습니다.”

### 프로젝트 규칙

- 적용된 Migration을 수정하지 않고 새 버전으로 변경한다.
- 빈 MySQL에서 모든 Migration을 적용하고 매핑·제약·영속 동작을 검증한다.
- API와 Worker는 각자의 DB에 독립적인 Migration 이력을 유지한다.
- validate가 모든 인덱스·CHECK·삭제 정책까지 완전하게 검사하는 것은 아니므로 별도 검증을 수행한다.

### 실패·배포 시 주의사항

- MySQL의 많은 DDL은 implicit commit을 유발하므로 여러 문장으로 된 Migration 전체가 일반 DML 트랜잭션처럼 롤백된다고 가정하지 않는다.
- MySQL의 atomic DDL은 지원되는 개별 DDL의 원자성과 관련되며 Migration 전체의 원자성과 같지 않다.
- 실패한 경우 실제 스키마와 이력을 확인한 뒤 복구한다. 원인 확인 없이 Flyway repair만 수행하지 않는다.
- 무중단 변경에는 컬럼 추가, 구·신 코드 호환, 데이터 이관, 이후 기존 컬럼 제거 같은 단계적 전략이 필요하다. 프로젝트에서 무중단 Migration까지 검증했다고 주장하지 않는다.

근거: [DB·JPA 신뢰성 컨벤션](../convention/database-jpa-reliability-convention.md), [MySQL implicit commit](https://dev.mysql.com/doc/refman/8.4/en/implicit-commit.html)

## 30. DB COMMIT 직후 응답이 끊기면 실패로 보고 다시 실행해도 되는가?

### 면접 답변

“아닙니다. 서버가 커밋했지만 클라이언트가 응답을 받지 못한 것일 수 있어, 응답 실패와 DB 처리 실패를 구분해야 합니다. 같은 요청 식별자로 기존 처리 결과를 확인하고 중복 반영 없이 다시 응답할 수 있도록 설계해야 합니다.”

### 프로젝트 연결과 주의사항

- 업로드 요청의 `requestId`와 Job의 UNIQUE, 기존 Job 조회 경로가 반복 완료 요청을 다루는 근거다.
- 재요청 때마다 새로운 식별자를 만들면 같은 업무가 별도 요청으로 처리될 수 있다.
- 식별자만 같다고 무조건 기존 결과를 반환하지 않고 사용자·대상·요청 내용이 같은지 검증한다.
- 결과 확인 시 복제 지연이 있는 읽기 복제본을 사용하면 아직 없다고 오판할 수 있으므로 조회 일관성을 고려한다.
- 금융 거래에 적용한다면 거래 식별자, 재조회, 정산·대사 정책까지 필요하다. OnFilm 경험을 금융 거래 구현 경험으로 바꾸어 말하지 않는다.
- 실제 COMMIT 응답 유실 장애를 주입해 검증한 사례라고 주장하지 않고 실패 모델과 설계 방향으로 설명한다.

근거: [MediaEncodeJobTransactionService](../../src/main/java/com/onfilm/domain/kafka/service/MediaEncodeJobTransactionService.java), [V1 Migration](../../src/main/resources/db/migration/V1__create_initial_schema.sql)

## 31. 동시성 테스트를 신뢰할 수 있게 작성하려면 무엇이 필요한가?

### 면접 답변

“각 요청이 독립된 트랜잭션과 DB 연결에서 실행되도록 하고, 경쟁하려는 시점을 동기화해야 합니다. 성공·실패 개수만 보지 않고 모든 작업이 끝난 뒤 별도 트랜잭션에서 최종 데이터와 불변식을 확인해야 합니다.”

### 검증 방법

- 테스트 데이터를 먼저 커밋해 다른 연결에서도 볼 수 있게 한다.
- CountDownLatch·Barrier 등으로 중복 검사 이후 INSERT, 같은 버전 조회 이후 UPDATE 등 원하는 경쟁 지점을 맞춘다.
- 비관적 락 테스트는 첫 트랜잭션이 락을 얻은 뒤 두 번째가 시도하도록 구성한다. 락을 잡은 스레드가 다른 스레드의 같은 락 획득까지 기다리는 Barrier는 테스트 자체를 교착시킬 수 있다.
- 타임아웃을 두고 Future의 예외를 수집한다. 작업 스레드 실패를 무시한 채 테스트를 통과시키지 않는다.
- 같은 토큰 발급·같은 참여 INSERT 등 업무별로 기대 결과를 명시한다.
- 하나의 EntityManager나 엔티티 인스턴스를 여러 스레드에 공유하지 않는다.
- 테스트 종료의 자동 롤백만으로 commit 결과를 검증했다고 말하지 않는다.

### 프로젝트 연결

MySQL 통합 테스트로 중복 INSERT와 락·버전 충돌을 확인했다. 테스트 파일과 실제 검증 범위를 다시 확인하고, 단순 병렬 호출만으로 모든 경쟁 시점을 재현했다고 설명하지 않는다.

근거: [DB·JPA 신뢰성 사례의 검증 결과](../problem-solving/11-database-jpa-reliability.md)

## 32. DB Connection Pool이 부족하면 크기부터 늘리면 되는가?

### 면접 답변

“먼저 연결을 왜 오래 점유하는지 확인해야 합니다. 느린 SQL, 잠금 대기, 긴 트랜잭션, 외부 호출, 연결 반환 누락이 원인일 수 있습니다. 풀을 키우면 대기가 줄 수도 있지만 DB 동시 부하가 커져 더 느려질 수도 있습니다.”

### 점검 순서

- active·idle·pending과 연결 획득 시간·타임아웃을 확인한다.
- 느린 SQL과 오래 열린 트랜잭션, 잠금 대기를 함께 확인한다.
- 요청 처리 중 S3·Kafka·BCrypt 같은 긴 작업을 수행하며 DB 연결을 보유하는지 확인한다.
- 서버 인스턴스 수 × 인스턴스별 풀 크기와 DB의 연결 한도를 함께 계산한다.
- REQUIRES_NEW가 바깥 연결을 유지한 채 추가 연결을 요구하는 경로도 확인한다.
- 재시도 backoff는 DB 트랜잭션·연결을 반환한 뒤 수행한다.

### 프로젝트 연결

긴 외부 작업을 트랜잭션에서 분리했고, k6와 모니터링으로 DB active·pending을 확인했다. 특정 실행에서 pending이 0이었다고 전체 부하에서 연결 문제가 없다고 보장하지는 않는다.

근거: [트랜잭션 경계 개선 사례](../problem-solving/09-transaction-boundary-and-external-io.md)

## 33. 같은 MySQL 서버에서 API와 Worker의 DB·계정을 분리한 이유는?

### 면접 답변

“초기 규모에서는 물리 서버 비용을 줄이면서도 데이터 소유권과 접근 권한을 분리하기 위해서입니다. API와 Worker가 상대 테이블을 직접 조회·변경하지 않도록 논리 DB와 계정, Migration 이력을 나눴습니다.”

### 프로젝트 구성과 한계

- API: `onfilm_api`, `onfilm_api_app`
- Worker: `onfilm_worker`, `onfilm_worker_app`
- 데이터 교환은 Kafka 메시지와 인증된 Callback API를 사용한다.
- DB 간 JOIN·FK·직접 조회를 사용하지 않는 것은 프로젝트 정책이다.
- MySQL 자체는 같은 서버의 여러 스키마에 걸친 트랜잭션을 지원할 수 있지만, 프로젝트에서는 그 결합을 만들지 않는다.
- CPU·메모리·I/O·장애 영역은 여전히 공유된다. 논리 분리가 물리적 장애 격리를 제공하는 것은 아니다.
- 부하나 장애 격리 요구가 커지면 물리 분리를 검토할 수 있지만 자동으로 해결되는 것은 아니다.

근거: [DB 소유권 정책](../decisions/api-worker-database-ownership-and-flyway-baseline-policy.md), [DB·JPA 신뢰성 컨벤션](../convention/database-jpa-reliability-convention.md)

## 34. DB Lock과 Lease는 무엇이 다른가? Lease 만료만으로 중복 실행이 완전히 막히는가?

### 면접 답변

“DB Lock은 트랜잭션 동안 데이터 접근을 제어하는 장치이고, Lease는 특정 처리자가 일정 시점까지 작업을 맡았다는 정보를 저장하는 방식입니다. 짧은 트랜잭션에서 작업을 선점하고 Lease를 기록한 뒤, DB 잠금을 풀고 외부 작업을 수행할 수 있습니다.”

### 프로젝트 연결

- Outbox는 비관적 락으로 처리 대상을 선점하고 `leaseUntil`로 만료 시점을 관리한다.
- 서버가 중단되어 작업이 끝나지 않으면 만료된 작업을 다시 선점할 수 있다.
- 긴 Kafka 발행 동안 DB 행 잠금을 유지하는 것과 Lease를 저장하는 것을 구분한다.

### 중요한 한계

- Lease가 만료돼도 이전 처리 프로세스가 실제로 종료됐다는 보장은 없다. 느린 이전 처리자와 새 처리자가 동시에 실행될 수 있다.
- 필요하면 소유자 토큰·선점 세대 번호를 비교하는 조건부 완료 처리나 fencing을 설계한다. 현재 코드에 해당 보호가 모두 구현됐다고 가정하지 않는다.
- DB 완료 반영을 막아도 이미 발생한 Kafka 전송까지 취소하지 못하므로 소비자 측 멱등성도 필요하다.
- 서버 간 시계 차이, 작업 시간, 갱신 정책과 재선점 시점을 고려해야 한다.

근거: [MediaEncodeOutbox](../../src/main/java/com/onfilm/domain/kafka/entity/MediaEncodeOutbox.java), [Outbox Repository](../../src/main/java/com/onfilm/domain/kafka/repository/MediaEncodeOutboxRepository.java)

## 학습 순서와 연결 문서

1. 1~3번: 검증과 제약조건의 책임
2. 4~6번: 동시 수정과 재시도 경계
3. 7~10번: 인덱스 선택 기준과 구조
4. 11~12번: 실행 계획을 읽고 실제 비용으로 판단하는 방법
5. 13번: 실제 DB에서 검증해야 하는 이유

추가 질문은 다음 순서로 연습한다.

1. 14~18번: ACID·격리 수준·MVCC·팬텀·Lost Update
2. 19~22번: 실제 잠금 범위·데드락·잠금 조회 옵션
3. 23~25번: 인덱스 저장 구조·페이징·실행 계획 진단
4. 26~29번: 문자열 비교·참조 무결성·제약·Migration
5. 30~34번: 응답 유실·동시성 검증·연결 풀·DB 소유권·Lease

- [Java·Spring·JPA 면접 질문과 답변](08-java-spring-jpa-interview-qa.md): 트랜잭션 프록시·전파와 영속성 컨텍스트 개념
- [Constraint 검사 기록](../review/database/mysql-constraint-audit.md): UNIQUE·NULL·FK 정책의 실제 근거
- [인덱스 전후 비교](../performance/mysql-index-before-after-comparison.md): 비교 조건과 한계까지 포함한 측정 결과
- [DB·JPA 신뢰성 컨벤션](../convention/database-jpa-reliability-convention.md): 이후 변경에도 적용할 규칙

과거 설계 제안 문서와 현재 구현이 다르면, 최신 코드·적용된 Migration·검증 기록을 기준으로 답변한다. 측정 없이 개선 효과를 추정해 성과로 말하지 않는다.
