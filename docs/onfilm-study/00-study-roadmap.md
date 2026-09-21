# OnFilm 프로젝트 전체 공부 순서

## 학습 목표

코드를 단편적으로 외우지 않고, 각 코드가 어떤 사용자 기능을 완성하기 위해 존재하는지 이해한다.

각 기능은 항상 다음 순서로 공부한다.

1. 사용자가 무엇을 하는 기능인지 확인한다.
2. HTTP 요청이 어느 Controller로 들어오는지 찾는다.
3. Controller와 Service의 역할을 확인한다.
4. Repository가 어떤 데이터를 조회하거나 저장하는지 확인한다.
5. Entity와 DB에 저장되는 값을 확인한다.
6. 해당 구조를 선택한 이유를 알아본다.
7. 오류가 발생했을 때 어떻게 처리되는지 확인한다.
8. 자동화 테스트가 무엇을 검증하는지 확인한다.

---

## 0. 프로젝트 공통 구조

- OnFilm이 해결하려는 사용자 문제
- API 서버와 Worker의 역할
- Controller → Service → Repository → DB 흐름
- Entity와 DTO의 차이
- Command와 Query 분리
- 트랜잭션의 역할
- JPA 영속성 컨텍스트
- Dirty Checking, `flush`, `commit`
- 예외가 HTTP 응답으로 변환되는 과정
- 하나의 요청을 코드에서 끝까지 추적하는 방법

---

## 1. 회원가입·로그인·토큰 관리

### 1.1 회원가입

- 회원가입 요청의 전체 흐름
- 회원가입 DTO와 입력값 검증
- `UserEmail`, `Username` 값 객체
- 비밀번호 암호화
- 이메일과 사용자 이름 중복 검사
- 애플리케이션 검사와 DB Unique Constraint의 차이
- `User` 생성
- 기본 `Person` 생성
- Cascade를 이용한 함께 저장
- 동시 회원가입의 중복 처리

### 1.2 로그인

- 이메일과 비밀번호 입력
- 암호화된 비밀번호 비교
- 인증 성공과 실패 처리
- Access Token 발급
- Spring Security Filter
- 로그인한 사용자를 식별하는 과정

### 1.3 토큰 관리

- Access Token과 Refresh Token의 역할
- Refresh Token DB 저장
- 토큰 해시 저장
- Refresh Token Rotation
- 이전 토큰 재사용 탐지
- 동시 재발급 요청 처리
- 로그아웃과 토큰 폐기
- 만료 토큰 정리

---

## 2. 프로필 관리와 개인 페이지 공유

### 2.1 User와 Person

- 계정 `User`와 프로필 `Person`을 분리한 이유
- 일대일 관계
- 연관관계의 주인
- `mappedBy`
- `attachPerson()`과 `attachUser()`
- 연관관계 편의 메서드
- `cascade = ALL`
- `orphanRemoval = true`

### 2.2 프로필 조회

- `publicId`를 이용한 프로필 조회
- 내부 `id`와 외부 `publicId`를 분리한 이유
- Controller → QueryService → Repository 흐름
- `findByPublicId()` 파생 쿼리
- 저장 키를 공개 URL로 변환
- Entity를 `ProfileResponse`로 변환
- 공개 페이지와 비공개 페이지

### 2.3 프로필 등록과 수정

- `UpdatePersonRequest`
- `@Valid`, `@NotBlank`, `@Size`, `@PastOrPresent`
- 로그인 사용자 확인
- 프로필 소유권 확인
- `changeBasicInfo()`
- SNS 목록 교체
- 프로필 태그 목록 교체
- 태그 정규화와 중복 방지
- Dirty Checking을 이용한 변경 저장

---

## 3. 필모그래피와 작품 참여 역할 관리

- 필모그래피 기능의 목적
- `Person`과 `Movie`의 관계
- 다대다 관계를 직접 사용하지 않은 이유
- 중간 Entity `MoviePerson`
- `MoviePersonRole`로 참여 역할을 분리한 이유
- 한 사람이 배우·감독·작가 역할을 함께 갖는 구조
- 배우 역할의 `CastType`
- 작품 참여 정보 등록·조회·수정·삭제
- 참여 역할 추가와 제거
- 중복 역할 차단
- 소유권 검사
- Command와 Query Service 분리

---

## 4. 프로필 이미지·갤러리 파일 관리

- 파일과 파일 정보의 차이
- DB에 URL 대신 Storage Key를 저장하는 이유
- Local Storage와 S3 구현 분리
- `StorageService` 인터페이스
- 프로필 이미지 업로드와 교체
- 갤러리 이미지 등록과 삭제
- 갤러리 순서 관리
- 같은 이미지의 중복 등록 차단
- DB 저장 실패 시 업로드 파일 처리
- 파일 삭제와 DB 트랜잭션의 차이
- 외부 파일 작업을 트랜잭션 밖에서 수행하는 이유

---

## 5. 스토리보드 관리

- 스토리보드 기능의 목적
- `StoryboardProject`
- `StoryboardScene`
- `StoryboardCard`
- 프로젝트 → 장면 → 카드 관계
- 프로젝트·장면·카드 생성과 수정 및 삭제
- 장면과 카드 순서 변경
- 하위 항목의 소유권 확인
- `@OrderColumn`
- 자식 삭제와 `orphanRemoval`
- 스토리보드 전체 조회
- LAZY Loading과 N+1
- Fetch Join을 이용한 SQL 감소
- 요청당 SQL 24회에서 3회로 줄인 과정
- AWS 반복 성능 측정

---

## 6. 영화·장르·트레일러 관리

- `Movie` Entity
- 영화 기본 정보 등록과 수정
- `changeBasicInfo()`
- 상영 시간·개봉 연도·관람 등급 검증
- `Genre`와 `MovieGenre`
- 장르 중복 등록 차단
- `Trailer`와 영화의 관계
- 트레일러 유형
- 영화 대표 이미지와 영상 파일
- 영화 삭제 시 연관 데이터 처리
- 관리자 기능과 사용자 기능의 경계

---

## 7. 영상 업로드·인코딩·작업 상태 관리

### 7.1 영상 업로드

- 원본 영상을 API 서버로 직접 보내지 않는 이유
- Presigned URL
- `requestId`와 `sourceKey`
- 업로드 요청 상태
- 업로드 완료 통지와 검증

### 7.2 인코딩 작업 생성

- `MediaEncodeJob`
- 인코딩 작업 상태
- API와 Worker를 분리한 이유
- Kafka를 사용한 이유
- 작업 정보와 Kafka 메시지의 차이

### 7.3 Transactional Outbox

- DB 저장과 Kafka 발행 사이의 메시지 유실 문제
- Outbox의 역할
- Job과 Outbox를 같은 DB 트랜잭션으로 저장
- 별도 Publisher의 메시지 발행
- `PENDING`, `PUBLISHING`, `PUBLISHED`, `DEAD`
- 발행 실패 재시도
- Lease를 이용한 처리 권한 관리
- 서버가 중단된 발행 작업 복구

### 7.4 Worker Inbox와 영상 변환

- Kafka 메시지 수신
- Inbox의 역할
- `jobId`를 이용한 중복 처리 방지
- Worker 처리 상태
- FFmpeg 영상 변환
- 결과 파일 업로드
- Worker 중단 후 작업 재개
- 변환 완료 작업의 Callback 재개

### 7.5 Callback과 실패 처리

- Worker가 API에 결과를 통지하는 과정
- 내부 Callback 인증
- 반복 완료 통지 처리
- 완료 결과가 뒤집히는 요청 차단
- 일시적 실패 재시도
- 반복 실패의 DLT 보관
- Kafka Offset과 Consumer Lag
- Consumer Lag이 0인데 작업이 멈출 수 있는 이유

---

## 8. 운영·모니터링·성능·배포

### 8.1 배포

- GitHub Actions
- JAR 빌드와 EC2 전달
- `current.jar` 심볼릭 링크
- systemd 서비스 실행
- 환경변수와 비밀값 관리
- Health Check
- 배포 실패와 Rollback

### 8.2 모니터링과 장애 해결

- 구조화 로그
- 요청과 작업 식별자
- Spring Boot Actuator Health
- Prometheus Metric
- Grafana Dashboard
- CPU·메모리·DB Connection 확인
- Kafka Consumer Lag 확인
- API → Kafka → Worker → Callback 장애 추적
- Callback 요청과 보안 필터 충돌
- 수정 후 전체 흐름 재검증
- Runbook 작성

### 8.3 성능 측정

- 성능 측정 가설
- k6 VU와 요청률
- 평균과 p95
- 오류율과 Dropped Iteration
- CPU·Heap·DB Connection 측정
- N+1 버전과 Fetch Join 버전 비교
- 동일 조건 반복 측정
- 20만 건 데이터의 인덱스 전후 비교
- `EXPLAIN` 실행 계획
- 측정 결과 정리

---

## 현재 진행 상황

2026-09-17 기준:

- 0번 프로젝트 공통 구조 학습·문서 기록 완료
- 1번 회원가입·로그인·토큰 관리 학습·문서 기록 완료
  - 회원가입 입력 검증·값 객체·BCrypt·중복 검사·User와 Person 저장
  - JWT 요청 인증·Refresh Token Rotation·재사용 탐지·동시 요청 처리
  - 로그아웃·쿠키·CSRF·인증과 인가·프로필 소유권 검사
  - 프론트엔드 자동 재발급·진행 중 요청 공유·오래된 토큰 정리
  - 테스트 코드 읽기와 현재 구현의 한계
- 2번 프로필 단원은 `publicId`, 기본 정보 변경, DTO 검증, User와 Person 관계 일부를 선행 학습함

상세 내용은 [회원가입·로그인·토큰 관리](./01-auth-signup-login-token.md)에 기록했다. 학습 완료는 코드 설명을 학습했다는 의미이며, 문서 기록 시 테스트를 새로 실행했다는 의미는 아니다.

다음 학습은 **2-1. 계정 User와 프로필 Person을 분리한 이유**부터 진행한다. 선행 학습한 개념은 짧게 복습하고 실제 기능 흐름과 연결한다.
