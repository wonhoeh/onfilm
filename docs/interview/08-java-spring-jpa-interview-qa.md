# Java·Spring·JPA 면접 질문과 답변

- 작성일: 2026-09-08
- 범위: Java 객체·컬렉션, Spring DI·트랜잭션·검증, JPA 연관관계·영속성 컨텍스트·조회 최적화·동시성
- 구성: 최초 질문 1~16번, 추가 예상 질문 17~40번
- 작성 기준: OnFilm 코드·컨벤션·측정 기록과 JPA/Hibernate 공식 문서
- 활용 방법: 각 항목의 ‘면접 답변’을 먼저 말하고, 추가 질문에 프로젝트 사례와 주의사항으로 설명한다.
- 주의: 프로젝트에서 실제 적용한 내용과 일반적인 대안을 구분한다. 예시 코드는 개념 설명용이며 실제 Repository 메서드와 같다고 가정하지 않는다.

## 1. Entity 생성자를 제한하고 정적 팩토리를 사용한 이유는?

### 면접 답변

“필수값이 빠지거나 잘못된 상태의 엔티티가 만들어지는 것을 막기 위해서입니다. JPA가 사용하는 기본 생성자는 `protected`, 비즈니스 생성자는 `private`으로 제한하고, 검증을 거치는 정적 팩토리로 생성하도록 했습니다.

또한 `create`, `issue`, `requested`처럼 이름으로 생성 목적과 초기 상태를 드러낼 수 있습니다. 자식 엔티티는 부모의 추가 메서드를 통해 생성해 연관관계 연결까지 함께 처리했습니다.”

### 프로젝트 사례와 주의사항

- `StoryboardProject.addScene()`에서 Scene 생성과 양방향 관계 연결을 함께 처리한다.
- 부모를 통해서만 생성하는 자식의 팩토리는 package-private으로 제한한다.
- 정적 팩토리 자체가 검증을 보장하지는 않는다. 생성 경로를 제한하고 그 안에 검증을 구현했기 때문에 보장되는 것이다.
- JPA의 객체 복원용 기본 생성자와 애플리케이션의 비즈니스 생성 경로를 구분한다.

근거: [StoryboardProject](../../src/main/java/com/onfilm/domain/movie/entity/StoryboardProject.java), [엔티티 리팩토링 가이드](../convention/entity-refactoring-style-guide.md)

## 2. Builder를 무조건 사용하지 않는 이유는?

### 면접 답변

“Builder는 선택 항목이 많을 때 가독성을 높여주지만, 일반적인 Builder만으로는 필수값이나 필드 간 조합을 강제하기 어렵습니다. 엔티티 전체 필드를 열어두면 ID나 처리 상태처럼 외부에서 정하면 안 되는 값까지 설정할 수 있습니다.

프로젝트에서는 생성 시 지켜야 할 규칙과 초기 상태가 중요했기 때문에 정적 팩토리를 우선했습니다. 다만 Builder도 검증하는 생성자를 거치도록 설계할 수 있으므로, Builder 자체를 나쁜 방식이라고 보지는 않습니다.”

### 주의사항

- 핵심은 Builder 사용 여부가 아니라 잘못된 객체를 만들 수 있는 생성 경로가 열려 있는지다.
- 정적 팩토리와 Builder는 반드시 양자택일해야 하는 관계가 아니다.
- 필수값과 상태 규칙이 중요한 엔티티인지, 선택 항목이 많은 값 객체인지에 따라 판단한다.

## 3. change, update, apply를 어떻게 구분했는가?

### 면접 답변

“메서드가 담당하는 범위를 이름으로 구분했습니다. `update`는 서비스에서 수행하는 전체 수정 작업, `change`는 엔티티의 의미 있는 상태 변경, `apply`는 생성과 변경에서 공유하는 내부 검증·값 반영에 사용했습니다.

예를 들어 Movie는 생성자와 `changeBasicInfo()`가 같은 검증 규칙을 사용하도록 `applyBasicInfo()`를 공유합니다. 다만 `reorder`, `remove`처럼 의도가 더 명확한 동사가 있으면 이를 우선했습니다.”

| 이름 | 의미 | 주요 위치 |
|---|---|---|
| `update` | 조회·권한 확인·변경 등 전체 수정 작업 수행 | Controller·Service |
| `change` | 도메인 규칙을 거친 상태 변경 | Entity의 공개 메서드 |
| `apply` | 생성과 변경이 공유하는 검증·정규화·필드 반영 | Entity의 내부 메서드 |

Java나 JPA의 공식 규칙이 아니라 프로젝트 컨벤션이다. 단순 변경까지 의미 없이 `apply`로 분리하지 않는다.

근거: [메서드 네이밍 컨벤션](../convention/entity-method-naming-convention.md), [Movie](../../src/main/java/com/onfilm/domain/movie/entity/Movie.java)

## 4. 양방향 연관관계에서 연관관계의 주인은 어느 쪽인가?

### 면접 답변

“연관관계의 주인은 DB의 관계 변경을 반영하는 쪽입니다. 일반적인 양방향 일대다·다대일 관계에서는 외래키를 가진 다대일 쪽이 주인입니다.

프로젝트에서는 `StoryboardScene.project`가 `project_id`를 관리하므로 주인이고, `StoryboardProject.scenes`는 반대쪽입니다.”

### 주의사항

- 도메인에서 자식의 생명주기를 관리하는 부모와 JPA 연관관계의 주인은 다른 개념이다.
- 부모가 Scene을 생성하더라도 FK를 관리하는 주인은 Scene 쪽이다.
- ‘항상 자식이 주인’으로 외우지 않는다. 여기서는 양방향 일대다·다대일 관계를 설명한 것이다.

근거: [StoryboardScene](../../src/main/java/com/onfilm/domain/movie/entity/StoryboardScene.java), [JPA 명세의 Entity Relationships](https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html)

## 5. mappedBy는 무엇인가?

### 면접 답변

“현재 필드가 연관관계의 주인이 아니며, 상대 엔티티의 어느 필드가 관계를 관리하는지 지정하는 속성입니다.”

```java
// StoryboardProject
@OneToMany(mappedBy = "project")
private List<StoryboardScene> scenes;
```

- `project`는 DB 컬럼명이 아니라 `StoryboardScene`의 Java 필드명이다.
- `mappedBy`가 양쪽 객체 참조를 자동으로 맞춰주는 것은 아니다.
- 반대쪽 컬렉션만 변경했다고 FK 변경까지 보장되는 것은 아니다. 관계 변경 시 주인 쪽 참조도 설정해야 한다.

공식 참고: [JPA 명세](https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html)

## 6. 연관관계 편의 메서드는 왜 필요한가?

### 면접 답변

“양방향 관계는 Java 객체에서는 서로 다른 두 참조이므로 한쪽을 바꿔도 다른 쪽이 자동으로 바뀌지 않습니다. 한 번의 호출로 양쪽 관계를 함께 맞추고, 중복 추가나 다른 부모로의 재할당 같은 잘못된 변경을 막기 위해 필요합니다.”

프로젝트의 `addScene()` 내부에서는 다음 두 작업을 함께 수행한다.

```java
requiredScene.attachProject(this);
scenes.add(requiredScene);
```

외부에는 컬렉션을 읽기 전용으로 노출하고, 추가·삭제·재정렬 메서드를 통해서만 변경하도록 했다. 삭제할 때도 부모 컬렉션과 자식의 부모 참조를 함께 정리한다.

근거: [StoryboardProject](../../src/main/java/com/onfilm/domain/movie/entity/StoryboardProject.java)

## 7. cascade = ALL과 orphanRemoval = true의 차이는?

### 면접 답변

“Cascade는 부모에 수행한 영속성 작업을 자식에게 전파하는 설정이고, orphanRemoval은 부모와의 관계에서 제거된 자식을 삭제하는 설정입니다. 프로젝트에서는 Scene이 Project에 종속된 데이터이므로 두 옵션을 함께 사용했습니다.”

| 상황 | `cascade = ALL` | `orphanRemoval = true` |
|---|---|---|
| 부모 저장 시 자식도 저장 | `PERSIST` 전파 | 이것만으로는 저장 전파 안 됨 |
| 부모 삭제 시 자식도 삭제 | `REMOVE` 전파 | 부모 삭제에 따른 자식 삭제도 지원 |
| 부모 컬렉션에서 자식만 제거 | 이것만으로 자식 삭제를 보장하지 않음 | 고아가 된 자식 삭제 |

### 주의사항

- `ALL`은 `PERSIST`, `MERGE`, `REMOVE`, `REFRESH`, `DETACH`를 포함한다.
- 고아 삭제는 일반적으로 관리 상태의 관계를 변경하고 flush할 때 반영된다. 새 객체·준영속 객체의 처리를 같은 방식으로 단정하지 않는다.
- 여러 부모가 공유하는 데이터에는 삭제 전파를 신중하게 적용한다.
- JPA Cascade와 DB의 `ON DELETE CASCADE`는 별개다.

공식 참고: [JPA 관계·고아 삭제 규칙](https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html)

## 8. Flush는 언제 발생하는가?

### 면접 답변

“Flush는 영속성 컨텍스트의 변경을 SQL로 DB에 반영하는 과정입니다. 일반적으로 트랜잭션 커밋 전, `flush()`를 직접 호출할 때, AUTO 모드에서 변경 내용이 조회 결과에 영향을 줄 수 있는 쿼리를 실행하기 전에 발생합니다.”

| 동작 | 의미 |
|---|---|
| `flush()` | 변경 내용을 DB에 반영 |
| `commit()` | 트랜잭션 결과 확정 |
| `clear()` | 관리 중인 엔티티를 영속성 컨텍스트에서 분리 |

### 주의사항

- Flush는 commit이 아니며 영속성 컨텍스트를 비우지도 않는다.
- 모든 SELECT 전에 무조건 flush되는 것은 아니다.
- FlushMode, 네이티브 쿼리 호출 API, 동기화 설정에 따라 동작이 달라진다.
- `IDENTITY` 식별자 생성 등에서는 명시적 flush 전에도 INSERT가 실행될 수 있다. 모든 SQL이 커밋까지 대기한다고 설명하지 않는다.

공식 참고: [Hibernate Flush 설명](https://docs.hibernate.org/orm/6.5/introduction/html_single/#flushing)

## 9. Flush 시점에 예외가 발생하면 트랜잭션은 어떻게 되는가?

### 면접 답변

“제약조건 위반 같은 영속화 오류로 flush가 실패하면 일반적으로 트랜잭션을 롤백해야 합니다. 앞서 SQL이 실행됐더라도 같은 DB 트랜잭션에서 아직 커밋되지 않았다면 함께 롤백됩니다.

예외를 catch했다고 트랜잭션이 다시 정상 상태가 되는 것은 아닙니다. 이미 rollback-only로 표시됐다면 이후 커밋할 수 없으므로, 실패한 트랜잭션을 종료하고 필요한 처리는 새 트랜잭션에서 수행해야 합니다.”

### 주의사항

- `save()` 호출 때가 아니라 나중의 flush·commit 때 오류가 드러날 수 있다.
- Spring의 트랜잭션 프록시가 메서드 반환 후 커밋하는 경우, 그 단계의 예외는 메서드 내부 `try-catch`로 잡히지 않을 수 있다.
- DB 롤백이 S3 업로드 같은 외부 작업까지 되돌리지는 않는다.
- ‘모든 JPA 예외는 무조건 전체 롤백’이라고 일반화하지 않는다. 예외 종류와 트랜잭션 상태를 구분한다.

공식 참고: [JPA 예외·트랜잭션 규칙](https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html)

## 10. Dirty Checking은 어떻게 동작하는가?

### 면접 답변

“Hibernate는 영속성 컨텍스트에서 관리하는 엔티티의 변경을 감지하고, flush 시 필요한 UPDATE를 실행합니다. 일반적으로 조회 당시의 상태를 저장해 두고 현재 상태와 비교하는 방식입니다.”

```java
// 개념 설명용 예시
@Transactional
public void changeTitle(Long id, String title) {
    StoryboardProject project = entityManager.find(
            StoryboardProject.class, id
    );
    project.changeTitle(title);
}
```

- 관리 상태의 엔티티를 변경했으므로 별도 `save()` 없이 변경 감지가 적용된다.
- 새 객체나 준영속 객체를 수정했다고 자동 저장되는 것은 아니다.
- 바이트코드 강화 설정에 따라 변경 추적 방식이 달라질 수 있다.
- 필드 변경 즉시 UPDATE가 실행되는 것과 변경 감지를 혼동하지 않는다.

공식 참고: [Hibernate 영속성 컨텍스트·변경 감지](https://docs.hibernate.org/orm/6.5/introduction/html_single/)

## 11. LAZY 연관관계에서 N+1이 왜 발생하는가?

### 면접 답변

“LAZY는 연관 데이터를 필요한 시점에 가져오는 설정이지, 여러 연관 데이터를 한 번에 가져오는 설정은 아니기 때문입니다. 목록을 한 번 조회한 뒤 각 엔티티의 아직 초기화되지 않은 연관 데이터에 접근하면 추가 SELECT가 반복될 수 있습니다.”

```java
// 개념 설명용 예시
List<StoryboardProject> projects = ...; // 목록 조회

for (StoryboardProject project : projects) {
    project.getScenes().size(); // 프로젝트별 Scene 추가 조회 가능
}
```

- 프로젝트 20개라면 목록 조회 1회에 Scene 조회 20회가 추가될 수 있다.
- 이미 로딩됐거나 Batch Fetch가 적용된 경우에는 쿼리 수가 달라진다.
- EAGER로 바꾼다고 해결되는 것도 아니다. 즉시 로딩도 별도 SELECT로 수행될 수 있다.

## 12. N+1을 어떻게 탐지했는가?

### 면접 답변

“p6spy로 API 요청에서 실행되는 SQL을 확인했습니다. Storyboard 목록을 조회할 때 프로젝트 ID만 달라지는 Scene 조회 SQL이 반복되는 것을 발견했습니다.

프로젝트 20개, Scene 200개 데이터에서 요청당 SQL이 24회 발생했고, 그중 20회가 프로젝트별 Scene 조회였습니다. Fetch Join 적용 후 같은 조건에서 SQL이 3회로 줄어든 것을 확인하고, k6로 응답 시간도 비교했습니다.”

### 측정 기록 해석

- ‘느려서 N+1이라고 생각했다’가 아니라 반복 SQL과 발생 지점을 확인했다는 점을 설명한다.
- 24회에는 권한 확인 등 다른 조회도 포함된다. 전체 요청의 SQL을 단순히 1 + 20 = 21회라고 설명하지 않는다.
- 24회에서 3회로 줄어든 결과는 당시 측정한 API 흐름의 기록이며, 현재 모든 조회가 같은 SQL 수를 보장한다는 뜻은 아니다.
- 2026-03-31 기록과 2026-09 AWS 재측정 수치는 별도 실험이다. 환경·부하 조건이 다른 수치를 섞지 않는다.

근거: [Storyboard 성능 측정 기록](../review/k6/result-storyboard.md)

## 13. Fetch Join은 어떤 상황에서 사용하는가?

### 면접 답변

“현재 조회에서 연관 데이터까지 반드시 사용할 때, 추가 조회 없이 함께 로딩하기 위해 사용합니다. 기본 매핑은 LAZY로 두고, 필요한 조회에서 로딩 범위를 명시하는 방식입니다.

프로젝트에서는 Storyboard 목록 응답을 만드는 과정에서 Scene 접근이 반복돼 Fetch Join을 적용했습니다. 다만 연관 데이터를 불필요하게 많이 가져오거나 여러 컬렉션을 동시에 가져오면 조회 행이 크게 늘 수 있어 범위를 제한해야 합니다.”

```sql
-- JPQL 개념 예시: Scene이 없는 Project도 포함한다.
select p
from StoryboardProject p
left join fetch p.scenes
where p.person.id = :personId
```

일반 JOIN은 조건·결과 구성에 사용되며, 그 자체로 연관 엔티티 초기화를 보장하지 않는다는 점이 다르다.

공식 참고: [Hibernate Fetch Join](https://docs.hibernate.org/orm/6.5/querylanguage/html_single/#association-fetching)

## 14. Collection Fetch Join과 Paging을 함께 사용하면 어떤 문제가 발생하는가?

### 면접 답변

“컬렉션을 조인하면 부모 하나가 자식 수만큼 여러 SQL 행으로 늘어납니다. 이 상태에서 행 기준 LIMIT를 적용하면 부모 단위 페이지와 맞지 않고, 자식 데이터가 중간에 잘릴 수 있습니다.

Hibernate에서는 이를 피하려고 전체 결과를 가져온 뒤 메모리에서 페이징할 수 있어, 데이터가 많으면 메모리 사용량과 응답 시간이 증가합니다.”

### 대안과 주의사항

1. 부모를 먼저 DB에서 페이징하고 컬렉션은 Batch Fetch로 조회한다.
2. 부모 ID만 먼저 페이징한 뒤 해당 ID들의 연관 데이터를 별도 조회한다.

- 두 번째 방법은 ID 조회 순서가 `IN` 조회 결과에 자동으로 유지되지 않으므로 정렬도 처리해야 한다.
- 단건 연관관계인 ManyToOne·OneToOne Fetch Join에는 같은 행 증폭 문제가 일반적으로 발생하지 않는다.
- Hibernate의 `hibernate.query.fail_on_pagination_over_collection_fetch=true`를 사용하면 해당 상황을 예외로 발견할 수 있다. 이는 검토 가능한 안전장치이며 프로젝트에 적용했다고 주장하지 않는다.

공식 참고: [Hibernate Fetch Join·페이징 주의사항](https://docs.hibernate.org/orm/6.5/querylanguage/html_single/#limits)

## 15. Batch Fetch는 왜 사용하는가?

### 면접 답변

“지연 로딩할 연관 데이터를 하나씩 조회하지 않고 여러 개를 묶어서 가져와 추가 쿼리를 줄이기 위해 사용합니다. 특히 부모 목록을 페이징하면서 컬렉션도 조회해야 할 때 유용합니다.”

```sql
-- 개념 예시
SELECT *
FROM storyboard_scene
WHERE project_id IN (1, 2, 3, 4, 5);
```

### 설정과 주의사항

- Hibernate의 `@BatchSize` 또는 `hibernate.default_batch_fetch_size`로 설정할 수 있다.
- Fetch Join처럼 반드시 한 SQL로 끝나는 것은 아니다.
- 한 번에 읽는 데이터량과 쿼리 수의 균형을 보고 크기를 정한다.
- 조회용 Batch Fetch와 INSERT·UPDATE를 묶어 전송하는 JDBC Batch는 다른 기능이다.
- 이 문서에서는 사용 목적과 대안으로 설명한다. 실제 적용 근거를 확인하지 않고 프로젝트 적용 사례로 말하지 않는다.

공식 참고: [Hibernate Batch Fetch](https://docs.hibernate.org/orm/6.5/introduction/html_single/)

## 16. Bulk Update 이후 Persistence Context를 왜 주의해야 하는가?

### 면접 답변

“JPQL Bulk Update는 엔티티를 하나씩 변경하는 것이 아니라 DB를 직접 수정하기 때문에, 이미 영속성 컨텍스트에 올라온 객체에는 변경 내용이 반영되지 않습니다. 따라서 DB와 메모리의 값이 달라질 수 있습니다.

기존 변경 내용을 먼저 flush하고, Bulk 작업 이후에는 영속성 컨텍스트를 clear한 뒤 필요한 데이터를 다시 조회하는 방식으로 처리했습니다.”

예를 들어 DB에서는 토큰이 폐기됐는데, 이미 조회한 `RefreshToken` 객체는 폐기 전 상태를 가지고 있을 수 있다.

프로젝트의 RefreshToken 쿼리에는 다음 설정이 있다.

```java
@Modifying(
    flushAutomatically = true,
    clearAutomatically = true
)
```

- 실행 전 flush: 아직 DB에 반영하지 않은 변경을 먼저 처리한다.
- 실행 후 clear: 오래된 값을 가진 관리 객체들을 분리한다.
- clear는 일부 객체만이 아니라 영속성 컨텍스트 전체를 비운다. 기존 객체 참조가 자동으로 최신 값이 되는 것은 아니다.
- Bulk Update는 일반적인 엔티티 변경의 낙관적 락 처리를 자동으로 수행하지 않는다. 필요한 버전 조건·증가는 쿼리에서 직접 고려해야 한다.
- 프로젝트의 토큰 폐기 쿼리는 `version`을 명시적으로 증가시킨다. 버전을 증가시키는 것과 요청자가 기대한 버전을 WHERE 조건으로 비교하는 것은 별개다.

근거: [RefreshTokenRepository](../../src/main/java/com/onfilm/domain/token/repository/RefreshTokenRepository.java), [Spring Data JPA Modifying Queries](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html#jpa.modifying-queries)

## 17. ==, equals, hashCode는 어떻게 다른가?

### 면접 답변

“참조형에서 `==`는 같은 객체를 가리키는지 비교하고, `equals()`는 클래스가 정의한 동등성을 비교합니다. `hashCode()`는 HashMap이나 HashSet에서 저장·검색에 사용할 해시값을 제공합니다. equals로 같은 객체라면 hashCode도 같아야 하므로 두 메서드를 함께 설계해야 합니다.”

### 주의사항

- 기본형의 `==`는 값을 비교한다. 참조형의 `==`와 구분한다.
- Object의 기본 `equals()`는 참조 동일성 기준이다. 값 비교는 해당 클래스가 재정의했을 때의 동작이다.
- 해시값이 같아도 다른 객체일 수 있다. 해시 충돌과 동등성은 별개다.
- HashMap 키나 HashSet 원소의 동등성에 쓰는 값을 저장 후 변경하면 검색·삭제가 예상대로 동작하지 않을 수 있다.

공식 참고: [Java Object 계약](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html)

## 18. JPA Entity의 equals와 hashCode를 작성할 때 무엇을 주의하는가?

### 면접 답변

“자동 생성 ID는 저장 전에는 없고 저장 후에 생길 수 있어, 이를 단순히 해시값에 사용하면 컬렉션에 넣은 뒤 해시값이 바뀔 수 있습니다. 아직 ID가 없는 서로 다른 객체를 같은 객체로 판단하지 않도록 해야 하며, 프록시와 실제 엔티티의 비교도 고려해야 합니다.

그래서 모든 엔티티에 Lombok의 equals·hashCode를 일괄 적용하기보다, 동등성 비교가 필요한지와 변하지 않는 식별 기준이 있는지 먼저 판단합니다.”

### 프로젝트 연결과 주의사항

- `StoryboardProject`에는 `@Getter`만 있고 동등성 메서드를 재정의하지 않았다. 따라서 `scenes.contains(scene)`은 현재 같은 인스턴스를 다시 넣는 상황을 기준으로 이해해야 한다.
- 컬렉션의 `contains()`만으로 서로 다른 객체나 동시 요청의 업무상 중복을 모두 차단할 수는 없다. 도메인 검증과 DB 제약을 별도로 고려한다.
- 양방향 연관관계와 변경 가능한 컬렉션을 equals·hashCode·toString에 무분별하게 넣으면 순환 호출이나 불필요한 지연 로딩이 발생할 수 있다.
- 자연키도 실제로 유일하고 변경되지 않는다는 조건을 확인한 뒤 사용한다.

근거: [StoryboardProject](../../src/main/java/com/onfilm/domain/movie/entity/StoryboardProject.java)

## 19. DTO에 record를 사용한 이유는? record는 완전히 불변인가?

### 면접 답변

“요청과 응답처럼 값을 전달하는 객체의 의도를 명확히 하고, 생성자·접근자·equals·hashCode 등의 반복 코드를 줄이기 위해 record를 사용했습니다. 필드 참조는 final이지만 참조하는 객체까지 불변으로 바뀌는 것은 아닙니다. List처럼 변경 가능한 값을 포함하면 방어적 복사를 검토해야 합니다.”

```java
// 개념 예시: 리스트 구조를 방어적으로 복사한다.
public record ItemNames(List<String> names) {
    public ItemNames {
        names = List.copyOf(names);
    }
}
```

- `List.copyOf()`도 리스트 원소가 가변 객체라면 그 객체까지 복제하지는 않는다.
- record는 자동으로 입력값을 검증해 주지 않는다. Bean Validation이나 생성자 검증이 필요하다.
- JPA 3.1 엔티티는 final 클래스 제한과 기본 생성자 요구가 있어 record를 그대로 엔티티로 사용하는 방식과 맞지 않는다. DTO와 엔티티의 역할을 구분한다.

근거: [DTO 스타일 컨벤션](../convention/dto-style-convention.md), [Java Record Classes](https://docs.oracle.com/en/java/javase/17/language/records.html)

## 20. unmodifiableList와 List.copyOf의 차이는? 읽기 전용 컬렉션이면 엔티티도 불변인가?

### 면접 답변

“`Collections.unmodifiableList()`는 원본 리스트를 읽기 전용으로 보여주는 뷰이므로 원본이 바뀌면 조회 결과에도 반영됩니다. `List.copyOf()`는 원본 리스트의 이후 구조 변경에 영향을 받지 않는 수정 불가능한 리스트를 제공합니다. 두 방식 모두 원소 객체까지 불변으로 만들지는 않습니다.”

### 프로젝트 연결

- `StoryboardProject.getScenes()`는 `Collections.unmodifiableList(scenes)`를 반환한다.
- 외부에서 `add`, `clear`를 호출하는 것은 막지만 부모의 `addScene()`으로 추가된 결과는 뷰에 반영된다.
- 반환된 Scene의 공개 변경 메서드까지 막는 것은 아니다. 컬렉션 변경 통제와 엔티티 상태 변경 통제는 별개다.
- `List.copyOf()`는 null 원소를 허용하지 않는다는 차이도 있다.

## 21. Checked Exception과 Unchecked Exception의 차이는? 트랜잭션과는 어떤 관계인가?

### 면접 답변

“Checked Exception은 호출자가 catch하거나 throws로 선언해야 하는 예외이고, Unchecked Exception은 그 의무가 없습니다. RuntimeException과 Error 계열은 unchecked에 해당합니다.

Spring의 일반적인 기본 롤백 규칙은 RuntimeException과 Error에는 롤백하고, checked exception에는 자동 롤백하지 않는 것입니다. 필요한 경우 `rollbackFor` 등으로 정책을 명시합니다.”

### 프로젝트 연결과 주의사항

- 프로젝트의 `DomainException`은 `RuntimeException`을 상속한다.
- 예외가 트랜잭션 경계를 빠져나왔는지, 이미 rollback-only 상태인지도 확인해야 한다. 단순히 예외를 catch하면 항상 커밋할 수 있다는 뜻은 아니다.
- 여기서는 프로젝트 계열의 일반적인 기본 설정을 설명한다. Spring 버전과 전역 설정·개별 롤백 규칙에 따라 정책을 바꿀 수 있다.
- 예외의 checked 여부가 HTTP 상태 코드를 자동 결정하는 것은 아니다.

근거: [DomainException](../../src/main/java/com/onfilm/domain/common/error/exception/DomainException.java), [Spring Transactional 설정](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)

## 22. DI는 무엇이며 생성자 주입을 사용한 이유는?

### 면접 답변

“DI는 객체가 사용할 의존 객체를 직접 생성하는 대신 외부에서 전달받는 방식입니다. 생성자 주입을 사용하면 필수 의존성이 명확하고 final 필드로 유지할 수 있습니다. 테스트에서도 Spring을 띄우지 않고 가짜 구현이나 Mock을 전달해 객체를 만들 수 있습니다.”

### 프로젝트 연결과 주의사항

- 서비스의 `final` 의존 필드와 `@RequiredArgsConstructor`를 사용한 구조가 생성자 주입에 해당한다.
- 의존성을 주입한다고 자동으로 결합도가 사라지는 것은 아니다. 인터페이스와 책임 분리도 함께 설계해야 한다.
- 생성자의 의존성이 지나치게 많으면 서비스 책임이 넓지 않은지 검토한다.
- 의존 객체의 참조가 final이어도 그 객체 내부 상태가 불변이라는 뜻은 아니다.

## 23. Spring의 Singleton Bean은 Thread-safe한가?

### 면접 답변

“Singleton은 컨테이너에서 빈 인스턴스를 공유한다는 뜻이지, 여러 스레드가 동시에 접근해도 안전하다는 뜻은 아닙니다. 사용자 ID나 요청 결과 같은 요청별 값을 서비스 필드에 저장하면 요청 간 데이터가 섞일 수 있습니다. 서비스는 가능한 한 상태를 보관하지 않고, 요청 데이터는 인자와 지역 변수로 전달해야 합니다.”

### 주의사항

- final Repository 필드를 주입하는 것과 요청별 엔티티를 서비스 필드에 저장하는 것은 다르다.
- `synchronized`는 같은 JVM 안의 해당 락을 사용하는 접근만 제어한다. 서버가 여러 대면 DB 중복이나 동시 변경을 이것만으로 막을 수 없다.
- 필요한 공유 상태가 있다면 동시성 자료구조와 원자적 연산 등을 별도로 설계한다.

## 24. @Transactional은 어떻게 동작하며 같은 클래스의 내부 호출에서는 왜 주의해야 하는가?

### 면접 답변

“Spring의 기본 프록시 방식에서는 메서드 호출을 프록시가 가로채 트랜잭션을 시작하거나 기존 트랜잭션에 참여하고, 실행 결과에 따라 커밋·롤백합니다. 같은 객체 안에서 메서드를 직접 호출하면 프록시를 지나지 않으므로 호출된 메서드의 Transactional 설정이 새로 적용되지 않습니다.”

### 프로젝트 연결과 주의사항

- 외부 작업을 조율하는 서비스와 DB 작업을 수행하는 TransactionService를 별도 Bean으로 분리했다.
- 외부 메서드에 이미 트랜잭션이 있으면 내부 호출도 그 트랜잭션 안에서 실행될 수 있다. ‘내부 호출이면 트랜잭션이 전혀 없다’는 뜻은 아니다.
- 내부 메서드에 `REQUIRES_NEW`를 붙여도 직접 호출하면 새 트랜잭션이 열리지 않는다.
- `private` 메서드에 애너테이션만 붙이는 방식으로 프록시 트랜잭션을 만들 수 없다.
- 여기서는 기본 프록시 모드를 설명하며 AspectJ 방식과 구분한다.

근거: [트랜잭션 경계 개선 사례](../problem-solving/09-transaction-boundary-and-external-io.md), [Spring 프록시 적용 규칙](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)

## 25. @Transactional(readOnly = true)는 쓰기를 완전히 막는가?

### 면접 답변

“읽기 전용 작업이라는 의도를 전달하고, 트랜잭션 매니저·JPA 구현체·드라이버가 최적화에 활용하도록 하는 설정입니다. Hibernate 연동에서는 flush와 변경 감지 비용을 줄이는 데 도움이 될 수 있지만, 모든 쓰기를 반드시 차단하는 보안 장치로 보면 안 됩니다.”

### 프로젝트 연결과 주의사항

- `StoryboardQueryService` 등 조회 서비스에 읽기 전용 트랜잭션을 사용한다.
- DB와 드라이버에 따라 쓰기를 거부할 수 있지만 이를 모든 환경의 보장으로 설명하지 않는다.
- 기존 쓰기 트랜잭션에 `REQUIRED`로 참여하면 내부 메서드의 readOnly 선언이 독립된 읽기 전용 트랜잭션을 만들지는 않는다.
- 읽기 전용 서비스에서 의도적으로 엔티티를 수정하는 코드를 작성하지 않고 책임을 분리한다.

근거: [StoryboardQueryService](../../src/main/java/com/onfilm/domain/movie/service/StoryboardQueryService.java)

## 26. REQUIRED와 REQUIRES_NEW의 차이는? 프로젝트에서는 왜 사용했는가?

### 면접 답변

“REQUIRED는 기존 트랜잭션이 있으면 참여하고 없으면 새로 만듭니다. REQUIRES_NEW는 기존 트랜잭션을 잠시 중단하고 독립된 트랜잭션을 시작합니다.

프로젝트에서는 만료 토큰 사용 기록과 폐기를 인증 실패 응답과 분리해 보존하기 위해 REQUIRES_NEW를 사용했습니다. 외부 인증 처리에서 예외가 발생해도 별도 트랜잭션으로 커밋한 기록은 유지됩니다.”

### 주의사항

- 반드시 프록시를 통한 호출이어야 한다.
- 바깥 트랜잭션이 DB 연결을 보유한 채 추가 연결을 요구할 수 있어 풀 고갈에 주의한다.
- 바깥쪽이 잠근 행을 안쪽에서 변경하면 대기·타임아웃이 생길 수 있다.
- 새 트랜잭션이 바깥쪽의 미커밋 변경을 당연히 볼 수 있다고 가정하지 않는다.

근거: [RefreshTokenSecurityTransactionService](../../src/main/java/com/onfilm/domain/token/service/RefreshTokenSecurityTransactionService.java), [Spring 전파 규칙](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html)

## 27. 예외를 catch했는데 UnexpectedRollbackException이 발생하는 이유는?

### 면접 답변

“같은 물리 트랜잭션에 참여한 내부 작업이 실패해 rollback-only로 표시됐는데, 바깥에서 예외를 잡고 정상 커밋을 시도하면 발생할 수 있습니다. catch는 Java 예외 흐름을 처리한 것이지, 이미 결정된 트랜잭션의 롤백 상태를 취소한 것이 아닙니다.”

### 대처

- 예외를 무조건 숨기지 않고 실패를 호출자에게 전달한다.
- 실패 이후에도 반드시 남겨야 할 기록이면 독립된 트랜잭션이 필요한지 검토한다.
- 모든 문제를 `REQUIRES_NEW`로 해결하지 않는다. 원자성을 깨도 되는 업무인지 먼저 판단한다.
- 예외 형태는 트랜잭션 매니저와 발생 시점에 따라 달라질 수 있다.

공식 참고: [Spring REQUIRED와 rollback-only](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html)

## 28. S3·FFmpeg·BCrypt를 DB 트랜잭션 밖으로 옮긴 이유는?

### 면접 답변

“외부 통신이나 오래 걸리는 연산이 끝날 때까지 DB 연결과 잠금을 유지하면 다른 요청까지 대기할 수 있기 때문입니다. 짧은 DB 조회와 검증, 트랜잭션 밖의 외부 작업, 최종 상태 재검증과 저장으로 나눴습니다.”

### 프로젝트 연결과 주의사항

- 파일 처리와 인증 연산에서 긴 작업을 DB 변경 범위와 분리했다.
- 분리한 사이에 데이터가 바뀔 수 있으므로 마지막 저장 시 권한·상태·버전 등 필요한 조건을 다시 확인한다.
- 새 파일 저장 후 DB 반영이 실패한 경우 보상 삭제를 수행하는 등 불일치에 대한 처리가 필요하다.
- 모든 검증을 트랜잭션 밖에서 한 번만 수행하고 무조건 저장하는 방식은 아니다.
- BCrypt는 네트워크 I/O가 아니라 CPU를 사용하는 연산이다. 분리 이유를 ‘긴 작업 동안 DB 자원을 점유하지 않기 위해서’로 설명한다.

근거: [트랜잭션 경계와 외부 작업 분리 사례](../problem-solving/09-transaction-boundary-and-external-io.md)

## 29. 파일 삭제를 AFTER_COMMIT 이벤트로 옮긴 이유와 한계는?

### 면접 답변

“파일을 먼저 삭제한 뒤 DB 작업이 롤백되면 DB에는 파일 참조가 남지만 실제 파일은 없어질 수 있습니다. 이를 막기 위해 DB 변경이 성공적으로 커밋된 이후 기존 파일을 삭제하도록 했습니다.”

### 프로젝트 연결과 한계

- `StorageFilesDeleteEventListener`에 `@TransactionalEventListener(phase = AFTER_COMMIT)`을 적용했다.
- 파일별 삭제 실패는 예외를 잡아 로그로 남긴다. 삭제 실패로 이미 커밋한 DB 변경을 되돌릴 수는 없다.
- `AFTER_COMMIT`은 자동 비동기 실행을 의미하지 않는다. 현재 리스너에는 `@Async`가 없다.
- 기본적으로 활성 트랜잭션이 없으면 해당 리스너는 실행되지 않는다. `fallbackExecution` 설정과 구분한다.
- 메모리 이벤트만으로는 DB 커밋 직후 프로세스가 종료될 때 실행을 보장할 수 없다. 삭제 보장이 필요하면 영속적인 삭제 작업 기록과 재시도 구조를 추가해야 한다.
- 리스너에서 추가 DB 저장이 필요하다면 별도 트랜잭션 경계를 검토한다.

근거: [StorageFilesDeleteEventListener](../../src/main/java/com/onfilm/domain/file/event/StorageFilesDeleteEventListener.java), [Spring Transaction-bound Events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html)

## 30. DTO, Entity, Service, DB에서 검증을 나눈 이유는?

### 면접 답변

“각 계층이 지켜야 하는 조건이 다르기 때문입니다. DTO는 요청 형식, Entity는 객체가 항상 지켜야 하는 규칙, Service는 권한과 다른 데이터의 존재 등 업무 조건을 확인합니다. DB는 동시 요청에서도 깨지면 안 되는 유일성·필수값·참조 무결성을 최종적으로 보장합니다.”

| 위치 | 예시 |
|---|---|
| DTO | 제목 필수, 길이, UUID 형식 |
| Entity | 잘못된 상태 전이 금지, 다른 부모로 재할당 금지 |
| Service | 현재 사용자가 수정 가능한지, 대상 데이터가 존재하는지 |
| DB | Unique, Not Null, FK |

- 같은 값이 여러 계층에서 확인돼도 목적이 다르면 의미가 있다. 규칙의 상수와 정책은 일관되게 관리한다.
- `@Column(nullable = false)`와 `@NotNull`은 같은 기능이 아니다. 현재처럼 Flyway가 스키마를 관리하면 실제 SQL 제약도 일치해야 한다.
- 애너테이션만 선언했다고 모든 호출 경로에서 Bean Validation이 자동 실행되는 것은 아니다. `@Valid` 등 적용 경로도 확인한다.

근거: [검증 흐름 컨벤션](../convention/validation-flow-convention.md)

## 31. 엔티티의 비영속·영속·준영속·삭제 상태는 어떻게 다른가?

### 면접 답변

“비영속은 새로 만들었지만 영속성 컨텍스트가 관리하지 않는 상태, 영속은 컨텍스트가 관리하는 상태입니다. 준영속은 관리하던 객체가 detach·clear·컨텍스트 종료 등으로 분리된 상태이고, 삭제 상태는 remove로 삭제가 예정된 상태입니다.”

### 주의사항

- 영속 상태라고 모든 SQL이 이미 실행되거나 커밋된 것은 아니다.
- `remove()` 호출과 실제 DELETE 실행 시점은 다를 수 있다.
- ID가 있다고 반드시 영속 상태인 것은 아니다.
- 준영속 객체를 변경해도 자동 변경 감지는 적용되지 않는다.

공식 참고: [JPA Entity Instance’s Life Cycle](https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html)

## 32. persist와 merge는 어떻게 다른가? Spring Data JPA save는 무엇을 하는가?

### 면접 답변

“persist는 새 엔티티를 영속성 컨텍스트가 관리하도록 하고, merge는 전달받은 객체의 상태를 관리 객체에 복사한 뒤 그 관리 객체를 반환합니다. merge에 전달한 원본 객체 자체가 영속 상태로 바뀌는 것은 아닙니다.

Spring Data JPA의 save는 새 엔티티로 판단하면 persist, 그렇지 않으면 merge를 사용합니다. 따라서 save가 항상 INSERT를 실행하거나 즉시 SQL을 보내는 것은 아닙니다.”

### 주의사항

- 새 엔티티 판별은 단순히 모든 경우에 ID null 여부만 보는 것이 아니다. 기본적으로 참조형 Version 속성을 먼저 고려하고, 그렇지 않으면 ID를 확인하며 `Persistable.isNew()`로 지정할 수도 있다.
- 수정 요청 DTO를 불완전한 엔티티로 만든 뒤 merge하면 원하지 않는 null 값까지 복사할 위험이 있다.
- 프로젝트처럼 엔티티를 조회하고 `change*()`를 호출하면 변경 규칙을 명확하게 적용할 수 있다.

공식 참고: [Spring Data JPA 엔티티 저장과 신규 판별](https://docs.spring.io/spring-data/jpa/reference/jpa/entity-persistence.html)

## 33. 1차 캐시는 무엇이고 동일성을 어디까지 보장하는가?

### 면접 답변

“1차 캐시는 영속성 컨텍스트가 관리하는 엔티티 저장 공간입니다. 같은 컨텍스트에서 같은 엔티티 타입과 식별자로 조회하면 같은 관리 객체를 사용합니다. 하지만 컨텍스트가 다르면 같은 DB 행이라도 다른 Java 객체일 수 있습니다.”

### 주의사항

- `find()`는 이미 관리 중인 객체가 있으면 DB 조회 없이 반환할 수 있다.
- JPQL 목록 조회가 1차 캐시에 있다는 이유만으로 항상 SQL을 생략하는 것은 아니다. 조회 결과의 식별자를 기존 관리 객체와 연결할 수 있다는 의미와 구분한다.
- 1차 캐시는 애플리케이션 전체가 공유하는 Redis 같은 캐시가 아니다.
- 외부 트랜잭션이나 Bulk Update가 바꾼 값을 자동으로 실시간 반영하지 않는다.
- 관리 객체의 동일성이 DB 트랜잭션 격리 수준 전체를 대신하지 않는다.

## 34. LazyInitializationException은 왜 발생하는가? OSIV는 무엇인가?

### 면접 답변

“일반적인 지연 로딩은 연관 데이터를 조회할 수 있는 영속성 컨텍스트가 필요합니다. 컨텍스트가 닫힌 뒤 아직 초기화되지 않은 연관 데이터에 접근하면 LazyInitializationException이 발생할 수 있습니다.

OSIV는 웹 요청 처리 동안 영속성 컨텍스트를 열어두는 방식입니다. Controller나 응답 직렬화 단계에서도 지연 로딩이 가능하지만, 서비스 밖에서 예상하지 못한 SQL이 발생하고 DB 연결 사용 시간이 늘어날 수 있습니다.”

### 대처와 주의사항

- 서비스의 적절한 조회 범위에서 필요한 데이터를 가져와 DTO로 반환한다.
- 조회 목적에 따라 Fetch Join, EntityGraph, DTO 직접 조회, Batch Fetch 등을 선택한다.
- 무조건 EAGER로 바꾸거나 OSIV를 켜는 것을 해결책으로 삼지 않는다.
- OSIV가 켜져 있다고 DB 트랜잭션도 웹 요청 전체에 걸쳐 유지되는 것은 아니다.
- 이 항목은 개념과 선택 기준이다. 배포 프로필을 확인하지 않고 프로젝트의 OSIV 설정값을 단정하지 않는다.

## 35. 낙관적 락과 @Version은 어떻게 동시 수정을 감지하는가?

### 면접 답변

“조회한 버전과 DB의 현재 버전이 같은 경우에만 변경하도록 조건을 걸어, 다른 트랜잭션이 먼저 변경했는지 감지합니다. Hibernate는 Version 필드를 이용해 UPDATE의 조건에 기존 버전을 포함하고, 변경 성공 시 버전을 증가시킵니다. 버전이 달라 업데이트하지 못하면 낙관적 락 충돌로 처리합니다.”

```sql
-- 개념 예시
UPDATE media_encode_job
SET status = ?, version = version + 1
WHERE id = ? AND version = ?;
```

### 프로젝트 연결과 주의사항

- `MediaEncodeJob`, `MediaEncodeOutbox`, `MediaUploadRequest`, `RefreshToken`에 `@Version`이 있다.
- 같은 종료 결과의 반복 요청인지, 서로 다른 변경의 충돌인지 구분한다. 모든 충돌을 무조건 재시도하지 않는다.
- 충돌 시 새 트랜잭션에서 다시 읽고 업무 조건을 재검증한다. 실패한 컨텍스트에서 그대로 반복하지 않는다.
- 낙관적 락도 UPDATE 실행 중 DB의 쓰기 잠금까지 없애는 것은 아니다.
- Bulk Update는 16번처럼 버전 조건을 직접 검토해야 한다.

근거: [MediaEncodeJob](../../src/main/java/com/onfilm/domain/kafka/entity/MediaEncodeJob.java), [JPA Optimistic Locking](https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html)

## 36. 비관적 락은 언제 선택하며 어떤 비용이 있는가?

### 면접 답변

“같은 데이터를 동시에 변경할 가능성이 높거나 먼저 처리 권한을 확보해야 할 때 선택합니다. PESSIMISTIC_WRITE로 필요한 데이터를 잠근 뒤 검증하고 변경해 경쟁 요청을 제어합니다. 대신 다른 트랜잭션이 대기할 수 있어 잠금 시간을 짧게 유지해야 합니다.”

### 프로젝트 연결과 주의사항

- `MediaUploadRequestRepository`와 `MediaEncodeOutboxRepository`에 `@Lock(PESSIMISTIC_WRITE)`가 있다.
- 잠금은 일반적으로 트랜잭션 종료까지 유지된다. 잠근 상태에서 S3·Kafka 통신이나 긴 인코딩을 수행하지 않도록 경계를 구분한다.
- 낙관적 락과 달리 먼저 DB 잠금을 확보하는 접근이지만, 일반 SELECT까지 모두 차단한다고 단정하지 않는다. MySQL MVCC·격리 수준·쿼리 조건에 따라 다르다.
- 인덱스와 조건에 따라 잠금 범위가 달라질 수 있다. 데드락·타임아웃 처리도 고려한다.

근거: [MediaUploadRequestRepository](../../src/main/java/com/onfilm/domain/kafka/repository/MediaUploadRequestRepository.java), [MediaEncodeOutboxRepository](../../src/main/java/com/onfilm/domain/kafka/repository/MediaEncodeOutboxRepository.java)

## 37. 중복 여부를 먼저 조회했는데도 Unique Constraint가 필요한 이유는?

### 면접 답변

“동시에 들어온 두 요청이 모두 ‘아직 없다’는 조회 결과를 받은 뒤 INSERT할 수 있기 때문입니다. 사전 조회는 사용자에게 빠르고 명확한 오류를 주는 데 도움이 되지만, 경쟁 상황의 최종 무결성은 DB의 Unique Constraint로 보장해야 합니다.”

### 프로젝트 연결과 주의사항

- 같은 Movie·Person 참여 관계와 같은 참여 관계의 역할 중복 등을 도메인 규칙과 DB 제약으로 관리한다.
- 동시성 테스트는 실제로 독립된 트랜잭션과 연결에서 경쟁시켜야 한다.
- 모든 DataIntegrityViolationException을 ‘중복’으로 변환하면 안 된다. FK·Not Null 위반 등과 구분한다.
- UNIQUE만 추가하고 끝내지 않고, 패배한 요청의 응답과 롤백 범위도 검증한다.

근거: [DB·JPA 신뢰성 개선 사례](../problem-solving/11-database-jpa-reliability.md)

## 38. Entity를 API 응답으로 직접 반환하지 않은 이유는?

### 면접 답변

“엔티티 구조가 바뀔 때 API 응답도 함께 바뀌는 결합을 줄이고, 공개할 데이터만 명확히 선택하기 위해 DTO를 사용했습니다. 엔티티를 직접 직렬화하면 양방향 관계의 순환 참조, 불필요한 지연 로딩, 내부 필드 노출이 생길 수 있습니다.”

### 주의사항

- DTO로 바꿨다는 이유만으로 N+1이 사라지는 것은 아니다. DTO 조립 과정에서 연관 객체를 순회하면 추가 조회가 발생할 수 있다.
- 필요한 데이터의 조회 전략과 응답 구성을 함께 설계한다.
- 요청 DTO도 엔티티와 분리해 클라이언트가 ID·상태·소유자 같은 내부 값을 임의로 설정하지 않도록 한다.

근거: [DTO 스타일 컨벤션](../convention/dto-style-convention.md)

## 39. GlobalExceptionHandler가 모든 예외를 처리하는가? 보안 필터는 어떻게 처리했는가?

### 면접 답변

“RestControllerAdvice 기반 예외 처리는 주로 Spring MVC의 요청 처리 과정에서 발생한 예외를 다룹니다. DispatcherServlet 이전의 보안 필터에서 발생한 예외까지 자동으로 모두 처리하는 것은 아닙니다.

프로젝트에서는 도메인 예외를 ErrorCode 기준으로 응답하고, 보안 필터의 인증·권한 오류도 공통 응답 형식으로 처리하도록 정리했습니다.”

### 주의사항

- Spring Security의 인증 실패는 AuthenticationEntryPoint, 접근 거부는 AccessDeniedHandler 등을 통해 처리할 수 있다. 커스텀 필터가 직접 실패를 처리하는 경로도 확인해야 한다.
- API 오류 코드를 통일하는 것과 예외를 잡는 위치를 하나로 만드는 것은 다르다.
- 원인 예외는 로그에 남기되 응답에 SQL, 토큰, 내부 경로 등을 그대로 노출하지 않는다.
- 401은 유효한 인증이 필요한 상황, 403은 접근이 거부된 상황으로 구분한다. CSRF 거부 등 403이 발생하는 경로도 함께 확인한다.

근거: [GlobalExceptionHandler](../../src/main/java/com/onfilm/domain/common/error/GlobalExceptionHandler.java), [예외·API 오류 표준화 사례](../problem-solving/08-domain-exception-and-api-error-standardization.md)

## 40. H2 테스트만으로 충분하지 않은 이유는? 트랜잭션 테스트에서 무엇을 확인했는가?

### 면접 답변

“H2와 MySQL은 SQL 문법, 타입, 제약조건, 잠금과 동시성 동작이 다를 수 있기 때문입니다. 프로젝트에서는 Testcontainers로 실제 MySQL을 실행하고, Flyway 적용과 Hibernate 매핑 검증, Repository와 트랜잭션 동작을 확인하도록 했습니다.”

### 테스트에서 중요한 구분

- 테스트 메서드에 붙인 `@Transactional`은 기본적으로 끝에 롤백될 수 있다. 테스트가 통과했다고 실제 commit이나 AFTER_COMMIT까지 검증한 것은 아니다.
- commit 후 다른 트랜잭션에서 다시 조회해 저장 결과를 확인한다.
- 실패 시 rollback, 독립 트랜잭션의 기록 보존, 잠금 충돌과 Unique 충돌을 각각 검증한다.
- 동시성 테스트는 하나의 트랜잭션 안에서 순서대로 호출하는 것으로 대체할 수 없다.
- Testcontainers가 운영 환경과 모든 조건을 같게 만드는 것은 아니다. DB 버전·격리 수준·문자셋·설정과 인프라 차이는 별도로 맞추고 기록해야 한다.

근거: [DB·JPA 신뢰성 개선 사례](../problem-solving/11-database-jpa-reliability.md)

## 추가 질문 학습 순서

처음부터 40개를 모두 외우기보다, 먼저 프로젝트 사례와 직접 연결되는 질문을 연습한다.

1. 24~29번: 트랜잭션 프록시, readOnly, 전파, rollback-only, 긴 작업 분리, 커밋 이후 이벤트
2. 35~37번: 낙관적·비관적 락과 DB Unique Constraint
3. 31~34번: 엔티티 생명주기, persist·merge, 1차 캐시, 지연 로딩
4. 17~23번: Java 동등성·불변성, 예외, DI와 Singleton
5. 30·38~40번: 검증 책임, DTO, 오류 응답, 실제 DB 테스트

심화 질문에는 ‘무조건’, ‘항상’, ‘자동으로 전부’ 같은 표현을 피하고, 적용 조건과 프로젝트에서 확인한 범위를 함께 설명한다.

## 함께 읽을 프로젝트 문서

- [엔티티 설계·리팩토링 가이드](../convention/entity-refactoring-style-guide.md)
- [엔티티 메서드 네이밍 컨벤션](../convention/entity-method-naming-convention.md)
- [엔티티 연관관계 정리](02-entity-relationship-guide.md)
- [서버 코드 최적화 가이드](05-server-optimization-jpa-nplus1.md)
- [트랜잭션과 외부 I/O 분리 사례](../problem-solving/09-transaction-boundary-and-external-io.md)
- [DB·JPA 신뢰성 개선 사례](../problem-solving/11-database-jpa-reliability.md)
