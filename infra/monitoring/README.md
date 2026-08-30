# Onfilm 로컬 모니터링

Prometheus가 Onfilm API와 Encoding Worker의 Actuator 메트릭을 수집하고 Grafana가 `Onfilm Media Operations` Dashboard를 자동으로 구성한다.

## 구성

| 구성 요소 | 로컬 주소 | 역할 |
|---|---|---|
| Onfilm API | `http://localhost:8080/actuator/prometheus` | Job·Outbox·Callback 메트릭 제공 |
| Encoding Worker | `http://localhost:8082/actuator/prometheus` | 단계·Inbox·복구·DLT 메트릭 제공 |
| Prometheus | `http://localhost:9090` | 15초 간격 수집, 기본 15일 보존 |
| Grafana | `http://localhost:3000` | Dashboard 조회 |

Prometheus와 Grafana 포트는 호스트의 `127.0.0.1`에만 바인딩한다. 이 구성은 로컬 개발용이며 인터넷에 공개하는 운영 구성이 아니다.

## 실행

API 저장소에서 dev 프로필로 API를 실행한다.

```bash
SPRING_PROFILES_ACTIVE=dev \
MEDIA_ENCODE_CALLBACK_SECRET=dev-media-encode-callback-secret-change-me \
./gradlew bootRun
```

별도 터미널의 Worker 저장소에서 Worker를 실행한다. Worker의 기본 HTTP·Actuator 포트는 `8082`다.

```bash
MEDIA_ENCODE_CALLBACK_SECRET=dev-media-encode-callback-secret-change-me \
METRICS_ENVIRONMENT=dev \
./gradlew bootRun
```

모니터링 디렉터리에서 로컬 환경 파일을 만든 후 컨테이너를 실행한다.

```bash
cd infra/monitoring
cp .env.example .env
docker compose up -d
docker compose ps
```

`.env`의 `GRAFANA_ADMIN_PASSWORD`를 로컬에서도 변경한다. `.env`는 Git에서 제외되며 `.env.example`만 저장한다.

## 확인 순서

1. API `http://localhost:8080/actuator/prometheus`가 텍스트 메트릭을 반환하는지 확인한다.
2. Worker `http://localhost:8082/actuator/prometheus`가 텍스트 메트릭을 반환하는지 확인한다.
3. Prometheus의 `http://localhost:9090/targets`에서 `onfilm-api`, `onfilm-encoding-worker`가 모두 `UP`인지 확인한다.
4. Grafana `http://localhost:3000`에 `.env`의 계정으로 로그인한다.
5. `Onfilm / Onfilm Media Operations` Dashboard를 연다.
6. 인코딩 Job을 실행한 뒤 Job·Outbox·Worker 패널의 값이 변하는지 확인한다.

다음 PromQL로 수집 상태를 빠르게 확인할 수 있다.

```promql
up{job=~"onfilm-.*"}
```

```promql
media_encode_job_records
```

```promql
media_encode_worker_inbox_records
```

## Dashboard 범위

- API Job 상태·종료율·처리 시간 p95
- Outbox 상태·PENDING 체류 시간·발행 및 재시도 결과
- API Callback 적용·중복·충돌·오류
- Worker 인코딩 성공·실패와 단계별 처리 시간 p95
- Worker 단계·오류 코드·재시도 가능 여부별 실패
- Inbox 상태·점유 결과·`FAILURE_PENDING` 체류 시간
- stale recovery·Callback·DLT 결과

Timer의 p95 계산을 위해 API와 Worker에서 Prometheus percentile histogram을 활성화한다.

## 문제 해결

### Prometheus Target이 DOWN인 경우

- API와 Worker가 각각 8080, 8082 포트에서 실행 중인지 확인한다.
- 브라우저나 `curl`로 각 `/actuator/prometheus`에 직접 접근한다.
- Docker Desktop에서 `host.docker.internal` 연결을 사용할 수 있는지 확인한다.
- Linux는 Compose의 `host-gateway` 설정을 사용하므로 Docker Engine 버전이 이를 지원해야 한다.

### Dashboard가 비어 있는 경우

- Prometheus `Targets`가 `UP`인지 먼저 확인한다.
- Dashboard의 `Application`, `Environment` 변수가 `All` 또는 올바른 값인지 확인한다.
- Counter·Timer는 실제 Job이나 Callback이 한 번 이상 실행된 후 생성될 수 있다.
- Gauge는 기본 30초 DB 스냅샷 주기 이후 갱신된다.

### Grafana 설정을 초기화해야 하는 경우

`docker compose down`은 컨테이너만 내리고 데이터 volume은 유지한다. 다음 명령은 Prometheus와 Grafana의 로컬 데이터를 모두 삭제하므로 필요한 경우에만 사용한다.

```bash
docker compose down -v
```

## 운영 적용 시 주의사항

- API prod 프로필의 관리 포트는 기본 `127.0.0.1:8081`이다. 같은 호스트의 Prometheus가 수집하거나, 별도 컨테이너에서 수집할 때만 방화벽으로 보호된 사설 주소에 바인딩한다.
- Worker의 Actuator 포트도 사설 네트워크에서만 접근하도록 제한한다.
- 운영 Grafana에서는 기본 계정을 사용하지 않고 강한 비밀번호 또는 조직 인증을 사용한다.
- Prometheus와 Grafana volume의 보존·백업 정책을 운영 환경에 맞게 별도로 설정한다.
- Alert rule과 알림 채널은 8단계에서 추가한다.

## 구성 검증

```bash
docker compose config
```

Dashboard JSON과 provisioning 파일은 애플리케이션 테스트에서도 파일 경로, datasource UID, 주요 메트릭 쿼리를 검증한다.
