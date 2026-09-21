# 백엔드 서버·네트워크·인프라 학습 순서

- 작성일: 2026-09-14
- 목표: 백엔드 요청이 서버와 네트워크를 거쳐 처리되는 흐름을 이해하고, OnFilm의 구성과 장애 원인을 자신의 말로 설명한다.
- 학습 방식: 개념 이해 → OnFilm 연결 → 명령어로 확인 → 장애 상황 추론 → 면접 답변 정리

## 전체 학습 순서

### 01. Linux와 서버 기본

- 서버의 두 가지 의미: 요청을 처리하는 프로그램과 그 프로그램이 실행되는 컴퓨터
- 운영체제와 애플리케이션의 관계
- 프로세스와 스레드
- CPU, 메모리, 디스크
- 사용자, 그룹, 파일 권한
- 환경변수와 설정 파일
- 포트와 소켓
- `systemd`, 로그, 종료 신호, Graceful Shutdown

OnFilm 연결:

- EC2, Java 프로세스, Spring Boot, Tomcat의 역할을 구분한다.
- `onfilm-api`와 `onfilm-worker`가 어떤 사용자와 프로세스로 실행되는지 설명한다.
- `systemctl`, `journalctl`, `ps`, `top`, `free`, `df`, `ss` 결과를 읽는다.

### 02. 네트워크 기초

- IP 주소와 포트
- 공인 IP와 사설 IP
- 네트워크, 서브넷, 기본 게이트웨이
- 라우팅
- TCP 연결과 3-way handshake
- DNS
- NAT와 인터넷 게이트웨이
- 방화벽과 AWS Security Group

OnFilm 연결:

- API, Worker, Kafka, RDS 사이의 출발지·목적지 IP와 포트를 그린다.
- 연결 실패 시 프로세스, 포트, 라우팅, 보안 규칙을 순서대로 확인한다.

### 03. HTTP와 웹 서버

- HTTP 요청과 응답
- 메서드, 상태 코드, Header, Body
- Cookie와 인증 정보 전달
- HTTP Keep-Alive
- Connection Timeout과 Read Timeout
- HTTPS와 TLS
- CORS와 CSRF
- Reverse Proxy와 Nginx
- Tomcat의 역할과 요청 처리 흐름

OnFilm 연결:

- 브라우저 요청이 Spring Controller에 도착하는 과정을 설명한다.
- 사용자 요청과 Actuator 관리 요청의 포트를 나눈 이유를 설명한다.
- Worker Callback이 브라우저 요청과 다른 보안 규칙을 사용한 이유를 설명한다.

### 04. Spring 서버의 자원 관리

- 요청 처리 스레드와 스레드 풀
- 동기 처리와 비동기 처리
- HikariCP와 DB Connection Pool
- 트랜잭션과 DB 연결 점유 시간
- 외부 API Timeout
- 오래 걸리는 작업 분리
- Graceful Shutdown

OnFilm 연결:

- HTTP 요청 수, Tomcat 스레드, DB Connection, 응답 시간의 관계를 설명한다.
- 영상 인코딩을 API에서 Worker로 분리한 이유를 CPU와 장애 격리 관점에서 설명한다.

### 05. 데이터베이스와 저장소

- 트랜잭션과 격리 수준
- Lock과 동시성
- 인덱스와 실행 계획
- DB Connection 관리
- 복제, 백업, 복구 기초
- 로컬 디스크와 객체 저장소의 차이

OnFilm 연결:

- Storyboard N+1과 인덱스 개선 결과를 DB 요청 수와 서버 자원 변화로 연결한다.
- RDS와 S3가 각각 어떤 데이터를 보관하는지 설명한다.

### 06. 시스템 아키텍처

- Client-Server Architecture
- 2-Tier와 3-Tier Architecture
- Tier와 Layer의 차이
- Layered Architecture
- Monolithic Architecture
- Stateful과 Stateless 서버
- Scale-up과 Scale-out
- Load Balancer
- Cache와 Message Queue
- 고가용성, 이중화, 단일 장애 지점
- MSA와 분산 시스템 기초

핵심 구분:

```text
3-Tier: Browser → API Server → Database Server
Layered Architecture: Controller → Service → Repository
```

OnFilm 연결:

- Frontend, API, Worker, Kafka, S3, MySQL의 책임과 연결 관계를 그린다.
- API와 Worker 분리가 단순한 프로세스 분리인지 독립 서비스인지 판단 기준을 설명한다.
- API 인스턴스를 여러 대로 늘릴 때 공유해야 하는 상태를 찾는다.

### 07. AWS 인프라

- Region과 Availability Zone
- VPC와 Subnet
- Route Table
- Internet Gateway와 NAT Gateway
- Security Group과 Network ACL
- EC2, RDS, S3, IAM
- Load Balancer와 Auto Scaling
- CloudWatch

OnFilm 연결:

- 각 EC2와 RDS가 어느 네트워크에 있고 어떤 통신만 허용했는지 설명한다.
- 인바운드와 아웃바운드 규칙 누락으로 연결이 실패했던 사례를 복기한다.

### 08. Docker와 배포

- Image와 Container
- Dockerfile
- Volume과 Container Network
- Docker Compose
- 환경변수와 Secret
- CI/CD
- Health Check
- 배포 전략과 Rollback

OnFilm 연결:

- Prometheus와 Grafana를 Compose로 실행한 구조를 설명한다.
- JAR 심볼릭 링크 교체, `systemd` 재시작, Health Check, Rollback 과정을 설명한다.

### 09. 모니터링과 장애 대응

- Log, Metric, Trace의 차이
- 처리량, 응답 시간, 오류율
- CPU, 메모리, 디스크
- Tomcat 스레드와 HikariCP Connection
- Kafka Consumer Lag
- 알림 기준과 Runbook

OnFilm 연결:

- Consumer Lag이 0인데 작업이 멈춘 장애를 단계별 기록과 Callback 로그로 추적한다.
- k6 부하 테스트와 Prometheus 지표를 같은 시간 구간으로 비교한다.

### 10. 분산 시스템과 안정성

- 메시지 기반 비동기 처리
- At-most-once, At-least-once, Exactly-once의 의미
- 멱등성과 중복 처리
- Transactional Outbox와 Inbox
- Retry, Backoff, DLQ
- Timeout과 Circuit Breaker
- 데이터 일관성과 최종적 일관성
- Lease와 장애 복구

OnFilm 연결:

- Job 저장과 Kafka 발행 사이의 메시지 누락 가능성을 설명한다.
- 반복 메시지와 Callback 순서 변경을 상태와 식별자로 처리한 방법을 설명한다.

### 11. 서버와 네트워크 보안

- 최소 권한 원칙
- 인증과 인가
- 전송 구간 암호화
- Secret 관리
- 공개 포트 최소화
- 내부 요청 인증
- 취약점 업데이트와 감사 로그

OnFilm 연결:

- 사용자 인증, 내부 Callback 인증, CSRF 검사의 책임을 구분한다.
- IAM과 Security Group이 각각 무엇을 허용하는지 설명한다.

## 권장 학습 진행 방법

각 소단원은 다음 순서로 학습한다.

1. 개념을 한두 문장으로 설명한다.
2. 요청 또는 데이터 흐름을 직접 그린다.
3. OnFilm에서 해당 개념이 사용된 위치를 찾는다.
4. 명령어, 로그 또는 지표로 실제 상태를 확인한다.
5. 구성요소 하나가 실패했을 때 나타날 현상을 예상한다.
6. 기본 질문과 꼬리질문에 말로 답한다.

## 학습 진도

- [ ] 01. Linux와 서버 기본
- [ ] 02. 네트워크 기초
- [ ] 03. HTTP와 웹 서버
- [ ] 04. Spring 서버의 자원 관리
- [ ] 05. 데이터베이스와 저장소
- [ ] 06. 시스템 아키텍처
- [ ] 07. AWS 인프라
- [ ] 08. Docker와 배포
- [ ] 09. 모니터링과 장애 대응
- [ ] 10. 분산 시스템과 안정성
- [ ] 11. 서버와 네트워크 보안

## 첫 번째 단원 시작점

Linux와 서버 기본 단원은 다음 순서로 진행한다.

1. 서버란 무엇인가
2. 운영체제와 애플리케이션의 관계
3. 프로그램과 프로세스
4. 프로세스와 스레드
5. CPU와 메모리 사용
6. 포트와 소켓
7. 사용자와 파일 권한
8. `systemd`와 서비스 관리
9. 로그 확인
10. 종료 신호와 Graceful Shutdown

