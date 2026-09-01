# OnFilm 로컬 MySQL

하나의 MySQL 서버에서 API와 Encoding Worker의 논리 DB와 계정을 분리하는 로컬 개발 구성이다.

## 구성

```text
MySQL 8.4.11
├── onfilm_api
│   └── onfilm_api_app
└── onfilm_worker
    └── onfilm_worker_app
```

- MySQL 포트는 호스트의 `127.0.0.1`에만 바인딩한다.
- API 계정에는 `onfilm_api.*`, Worker 계정에는 `onfilm_worker.*` 권한만 부여한다.
- 초기 Flyway 도입 단계에서는 각 서비스 계정이 자기 DB의 Migration과 Runtime 접근을 함께 담당한다.
- 공개 운영 단계에서는 DDL Migration 계정과 DML Runtime 계정의 추가 분리를 검토한다.
- 애플리케이션 Flyway Migration은 DB 생성이나 `GRANT`를 실행하지 않는다. 이 디렉터리의 초기화 스크립트가 인프라 수준의 생성을 담당한다.

정책과 트레이드오프는 [API와 Worker의 DB 소유권 및 Flyway 초기화 정책](../../docs/decisions/api-worker-database-ownership-and-flyway-baseline-policy.md)을 따른다.

## 요구 사항

- Docker Desktop 또는 Docker Engine이 실행 중이어야 한다.
- 호스트의 `MYSQL_PORT`가 비어 있어야 한다. 기본값은 `3306`이다.

## 파일

| 파일 | 역할 |
|---|---|
| `docker-compose.yml` | MySQL 8.4.11과 영속 Volume 실행 |
| `.env.example` | Git에 저장할 수 있는 환경변수 이름과 예제 |
| `init/01-create-service-databases.sh` | 첫 기동 시 두 DB·계정 생성과 권한 부여 |
| `scripts/verify-isolation.sh` | 자기 DB 접근 허용과 상대 DB 접근 거부 검증 |

## 실행

`.env.example`을 복사하고 세 비밀번호를 로컬 전용 값으로 변경한다.

```bash
cd infra/mysql
cp .env.example .env
docker compose config
docker compose up -d
docker compose ps
```

`.env`는 Git에서 제외되고 `.env.example`만 저장한다. 예제 비밀번호를 그대로 사용하지 않는다.

MySQL이 `healthy` 상태가 되면 다음 명령으로 계정 격리를 검증한다.

```bash
docker compose exec -T mysql /opt/onfilm/mysql/verify-isolation.sh
```

정상 결과는 다음 네 조건을 모두 만족한다.

- `onfilm_api_app`은 `onfilm_api`에 접근할 수 있다.
- `onfilm_api_app`은 `onfilm_worker`에 접근할 수 없다.
- `onfilm_worker_app`은 `onfilm_worker`에 접근할 수 있다.
- `onfilm_worker_app`은 `onfilm_api`에 접근할 수 없다.

## 애플리케이션 접속 정보

API를 호스트에서 실행할 때 다음 값을 사용한다.

```text
DB_URL=jdbc:mysql://127.0.0.1:3306/onfilm_api
DB_USER=onfilm_api_app
DB_PASSWORD=<ONFILM_API_DB_PASSWORD과 같은 값>
```

Worker를 호스트에서 실행할 때 다음 값을 사용한다.

```text
WORKER_DB_URL=jdbc:mysql://127.0.0.1:3306/onfilm_worker
WORKER_DB_USER=onfilm_worker_app
WORKER_DB_PASSWORD=<ONFILM_WORKER_DB_PASSWORD과 같은 값>
```

`MYSQL_PORT`를 변경했다면 JDBC URL에도 같은 포트를 사용한다. 애플리케이션을 Docker 네트워크 안에서 실행할 때는 호스트 이름과 네트워크 구성을 해당 Compose 환경에 맞게 지정한다.

## 중지와 데이터 보존

다음 명령은 컨테이너만 중지하고 `onfilm-mysql-data` Volume은 유지한다.

```bash
docker compose down
```

MySQL 공식 이미지의 `/docker-entrypoint-initdb.d` 스크립트는 데이터 디렉터리가 비어 있는 최초 기동에만 실행된다. Volume이 만들어진 뒤 `.env`의 비밀번호만 변경해도 기존 계정 비밀번호는 자동으로 바뀌지 않는다.

로컬 데이터를 모두 버리고 초기화 스크립트를 다시 실행해야 할 때만 다음 명령을 사용한다.

```bash
docker compose down -v
```

이 명령은 `onfilm_api`와 `onfilm_worker`의 로컬 데이터를 복구할 수 없게 삭제한다. 대상이 로컬 개발용 Volume인지 확인한 뒤 실행한다.

## API Flyway V1 최초 적용

API는 `V1__create_initial_schema.sql`부터 빈 `onfilm_api` DB에 적용하며 `baseline` 또는 `baselineOnMigrate`를 사용하지 않는다. 과거 Hibernate `create`로 테이블을 만든 로컬 Volume이 남아 있으면 Flyway가 이력 없는 비어 있지 않은 스키마를 거부하는 것이 정상이다.

보존할 로컬 데이터가 없고 API V1을 처음 적용하는 경우에만 위의 `docker compose down -v`로 Volume을 초기화한 뒤 Compose를 다시 시작한다. 이후에는 적용된 Migration 파일을 수정하거나 Volume을 습관적으로 삭제하지 않고, 새 버전 Migration으로 변경한다.

## 보안 및 운영 주의사항

- 이 Compose 구성은 로컬 개발용이며 그대로 운영 DB를 배포하는 템플릿이 아니다.
- 실제 비밀번호, 운영 JDBC URL과 Secret을 Git 또는 문서에 기록하지 않는다.
- 운영에서는 DB를 Private Network에 두고 인바운드와 계정 접속 출처를 제한한다.
- 같은 MySQL 서버를 사용하므로 CPU, Connection, 스토리지와 장애 영역은 공유된다.
- API와 Worker의 독립적인 가용성 또는 확장이 필요해지면 물리 MySQL 분리를 검토한다.

## 구성 검증

컨테이너를 실행하지 않고 Compose 환경변수와 구문을 확인한다.

```bash
cp .env.example .env
docker compose config
```

Shell 스크립트의 정적 구문은 다음 명령으로 확인한다.

```bash
bash -n init/01-create-service-databases.sh
bash -n scripts/verify-isolation.sh
```
