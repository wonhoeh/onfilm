# AWS 단기 성능 측정 환경 구축 기록

- 작성일: 2026-09-05
- 리전: `ap-northeast-2` (서울)
- 목적: OnFilm API·Encoding Worker·Kafka·MySQL을 실제 AWS 환경에 배치하고 k6 성능 수치와 장애 대응 증거를 수집한다.
- 적용 범위: 짧은 기간 동안만 사용하는 성능 측정 환경

## 1. 문서의 성격과 주의사항

이 문서는 OnFilm의 장기 운영 아키텍처가 아니라 짧은 시간 안에 성능을 측정하기 위해 구성한 임시 환경의 실제 구축 순서를 기록한다.

비용과 구축 시간을 줄이기 위해 Default VPC와 단일 Kafka Broker를 사용하고 Bastion Host, ALB, NAT Gateway, Multi-AZ와 Auto Scaling은 구성하지 않았다. GitHub-hosted Runner에서 EC2로 직접 SCP 배포하기 위해 API EC2의 SSH 포트를 성능 측정 기간에만 임시로 열었다.

따라서 다음 구성을 장기 운영 환경에 그대로 적용하면 안 된다.

- Kafka는 Broker와 Controller를 한 인스턴스에서 실행하며 복제 계수도 1이다.
- Kafka 통신은 보안 그룹 내부의 PLAINTEXT 통신이다.
- API EC2의 SSH `22` 포트는 GitHub Actions 배포를 위해 임시로 `0.0.0.0/0`에 허용한다.
- HTTPS와 Cookie Secure 정책은 별도의 운영 배포 단계에서 적용해야 한다.
- 성능 측정을 마치면 임시 SSH 규칙과 AWS 리소스를 정리한다.

## 2. 전체 구성과 완료 상태

초기 리소스는 다음과 같이 생성했다.

```text
Default VPC
├── API EC2
├── Encoding Worker EC2
├── Kafka EC2
├── k6·Prometheus·Grafana EC2
├── RDS MySQL
└── S3 Bucket: onfilm-s3-bucket
```

내부 통신은 사설 IP를 사용한다.

```text
k6 ───────────────▶ API:8080
Worker ───────────▶ API:8080       Callback
API ──────────────▶ Kafka:9092     작업 발행
Worker ───────────▶ Kafka:9092     작업 소비
API·Worker ───────▶ RDS:3306
API·Worker ───────▶ S3
```

외부 접속은 다음 용도로만 공인 IP 또는 탄력적 IP를 사용한다.

- 개발자 Mac에서 EC2로 SSH 접속
- GitHub Actions에서 API·Worker EC2로 JAR 전송

현재 확인된 사설 주소는 다음과 같다.

| 대상 | 사설 IP | 용도 |
|---|---|---|
| API EC2 | `172.31.23.111` | Worker Callback, k6 내부 호출 |
| Kafka EC2 | `172.31.27.63` | API·Worker Kafka Bootstrap |

사설 IP는 재구축 시 달라질 수 있으므로 실제 환경에서는 `hostname -I`로 다시 확인한다.

현재까지 완료한 범위는 다음과 같다.

- RDS 네트워크 연결과 마스터 계정 접속 확인
- `onfilm_api`, `onfilm_worker` 논리 DB와 전용 계정 분리
- API·Worker의 상대 DB 접근 차단 확인
- API·Worker EC2 IAM Role과 S3 조회·업로드·삭제 확인
- Kafka 4.3.1 KRaft 단일 Broker 설치와 systemd 실행
- 원본 Topic과 DLT 생성
- API·Worker에서 Kafka `9092` 연결 확인
- API EC2 실행 사용자, 환경변수와 systemd 구성
- GitHub Actions의 단위 테스트·MySQL 통합 테스트·SCP 배포 통합
- 첫 API 자동 배포와 Actuator Health 검사 성공

아직 남은 범위는 다음과 같다.

- Worker EC2 환경변수·systemd·GitHub Actions 배포
- k6·Prometheus·Grafana EC2 구성
- 외부·내부 API 기능 점검과 테스트 데이터 준비
- 성능 측정, 병목 확인과 수치 기록
- 측정 종료 후 보안 규칙과 AWS 리소스 정리

## 3. RDS 연결과 논리 DB·계정 분리

### 3.1 RDS Endpoint 확인

RDS 접속에는 ARN이 아니라 RDS 화면의 Endpoint를 사용한다.

```text
AWS Console
→ RDS
→ Databases
→ 대상 DB Instance
→ Connectivity & security
→ Endpoint & port
```

Endpoint 예시는 다음 형태다.

```text
onfilm-db.xxxxxxxxxxxx.ap-northeast-2.rds.amazonaws.com
```

다음 값들은 Endpoint가 아니다.

```text
arn:aws:rds:...
https://...
jdbc:mysql://...
```

CLI의 `-h`에는 호스트 이름만 전달한다.

### 3.2 RDS 보안 그룹 설정

RDS에는 SSH 규칙을 추가하지 않는다. API와 Worker 보안 그룹을 출발지로 하는 MySQL 규칙만 추가한다.

| Type | Protocol | Port | Source |
|---|---|---:|---|
| MySQL/Aurora | TCP | 3306 | API EC2 Security Group |
| MySQL/Aurora | TCP | 3306 | Worker EC2 Security Group |

보안 그룹 변경은 거의 즉시 적용되므로 RDS를 재부팅하지 않는다.

### 3.3 API EC2에서 마스터 계정 접속

RDS가 Private 접근만 허용하므로 개발자 Mac이 아니라 API EC2에서 접속했다.

Ubuntu에서 MySQL Client를 설치한다.

```bash
sudo apt update
sudo apt install mysql-client -y
```

포트 연결을 먼저 확인할 수 있다.

```bash
nc -zv -w 5 RDS_ENDPOINT 3306
```

마스터 계정으로 접속한다.

```bash
mysql --connect-timeout=10 \
  -h RDS_ENDPOINT \
  -P 3306 \
  -u MASTER_USERNAME \
  -p
```

`Enter password:` 이후에는 입력 글자나 별표가 표시되지 않는다. 비밀번호를 입력한 뒤 Enter를 누른다.

다음 오류는 네트워크 연결은 성공했지만 계정 또는 암호가 틀렸다는 의미다.

```text
ERROR 1045 (28000): Access denied for user ...
```

마스터 암호를 Secrets Manager로 관리했다면 Secret에서 확인한다. 직접 입력한 암호를 잊었다면 기존 값을 조회할 수 없으므로 RDS Modify 화면에서 재설정한다.

### 3.4 API·Worker DB와 계정 생성

하나의 RDS MySQL Instance 안에 두 개의 논리 DB를 만들되 소유 계정을 분리한다.

```text
RDS MySQL
├── onfilm_api
│   └── onfilm_api_app
└── onfilm_worker
    └── onfilm_worker_app
```

마스터 계정의 `mysql>` Prompt에서 실행한다. 아래 비밀번호 Placeholder는 실제로 서로 다른 강한 암호로 교체한다.

```sql
CREATE DATABASE onfilm_api
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'onfilm_api_app'@'%'
    IDENTIFIED BY 'API_APP_PASSWORD';

GRANT ALL PRIVILEGES
    ON onfilm_api.*
    TO 'onfilm_api_app'@'%';

CREATE DATABASE onfilm_worker
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'onfilm_worker_app'@'%'
    IDENTIFIED BY 'WORKER_APP_PASSWORD';

GRANT ALL PRIVILEGES
    ON onfilm_worker.*
    TO 'onfilm_worker_app'@'%';
```

현재 환경은 애플리케이션 시작 시 Flyway가 Migration을 적용하므로 각 애플리케이션 계정에 자기 DB의 DDL 권한을 포함한 `ALL PRIVILEGES`를 부여했다. 장기 운영 환경에서는 Flyway 전용 계정과 Runtime 계정을 추가로 분리할 수 있다.

마스터 계정은 인프라 관리에만 사용하고 API·Worker Runtime 설정에는 넣지 않는다.

### 3.5 DB 권한 분리 검증

API EC2에서 API 계정으로 접속한다.

```bash
mysql --connect-timeout=10 \
  -h RDS_ENDPOINT \
  -P 3306 \
  -u onfilm_api_app \
  -p \
  onfilm_api
```

```sql
SELECT CURRENT_USER(), DATABASE();

CREATE TABLE connection_check (
    id BIGINT PRIMARY KEY
);

DROP TABLE connection_check;

USE onfilm_worker;
```

마지막 명령이 `ERROR 1044 Access denied`를 반환해야 권한 분리가 정상이다.

Worker EC2에서도 반대 방향으로 같은 검증을 수행한다.

```bash
mysql --connect-timeout=10 \
  -h RDS_ENDPOINT \
  -P 3306 \
  -u onfilm_worker_app \
  -p \
  onfilm_worker
```

```sql
SELECT CURRENT_USER(), DATABASE();

CREATE TABLE connection_check (
    id BIGINT PRIMARY KEY
);

DROP TABLE connection_check;

USE onfilm_api;
```

Worker도 API DB 접근에서 `ERROR 1044`가 나와야 한다.

API와 Worker는 상대 DB를 직접 조회하거나 JOIN하지 않고 Kafka Message와 인증된 Callback API로 데이터를 교환한다.

## 4. EC2 IAM Role과 S3 접근

### 4.1 Access Key 대신 EC2 IAM Role 선택

IAM 사용자에서 발급한 `Access Key`와 `Secret Key`를 YML에 직접 넣는 기존 방식을 사용하지 않았다.

EC2에 IAM Role을 연결하면 AWS SDK가 Instance Metadata Service에서 임시 자격 증명을 가져오고 자동 갱신한다. 따라서 다음 환경변수는 설정하지 않는다.

```text
S3_ACCESS_KEY
S3_SECRET_KEY
```

처음 발급한 장기 Access Key가 이번 작업만을 위한 것이었다면 Role 검증 후 비활성화하거나 삭제한다.

### 4.2 버킷 한정 S3 정책 생성

`AmazonS3FullAccess` 대신 `onfilm-s3-bucket`에 필요한 권한만 가진 고객 관리형 정책 `OnfilmMediaBucketAccess`를 만들었다.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "BucketAccess",
      "Effect": "Allow",
      "Action": [
        "s3:ListBucket",
        "s3:GetBucketLocation"
      ],
      "Resource": "arn:aws:s3:::onfilm-s3-bucket"
    },
    {
      "Sid": "ObjectAccess",
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:DeleteObject"
      ],
      "Resource": "arn:aws:s3:::onfilm-s3-bucket/*"
    }
  ]
}
```

정책 하나를 다음 두 EC2 Role에 연결한다.

```text
OnfilmMediaBucketAccess
├── onfilm-api-ec2-role
└── onfilm-worker-ec2-role
```

IAM Role은 EC2 Console의 다음 경로에서 각 인스턴스에 연결한다.

```text
EC2 → Instance 선택 → Actions → Security → Modify IAM role
```

재부팅은 필요 없다.

### 4.3 AWS CLI와 S3 권한 검증

Ubuntu에서 AWS CLI가 없다면 설치한다.

```bash
sudo apt update
sudo apt install awscli -y
```

IAM Role을 사용할 때는 `aws configure`로 장기 Access Key를 저장하지 않는다.

API와 Worker EC2 각각에서 Role 인식을 확인한다.

```bash
aws sts get-caller-identity
```

`Arn`에 `assumed-role/...`이 표시되어야 한다.

버킷 조회를 확인한다.

```bash
aws s3 ls s3://onfilm-s3-bucket --region ap-northeast-2
```

API 업로드·삭제 검증 예시:

```bash
echo "onfilm s3 permission check" > /tmp/onfilm-s3-check.txt
aws s3 cp /tmp/onfilm-s3-check.txt \
  s3://onfilm-s3-bucket/permission-check/api.txt \
  --region ap-northeast-2
aws s3 ls s3://onfilm-s3-bucket/permission-check/api.txt \
  --region ap-northeast-2
aws s3 rm s3://onfilm-s3-bucket/permission-check/api.txt \
  --region ap-northeast-2
```

Worker에서는 Object Key만 `permission-check/worker.txt`로 바꿔 같은 검증을 수행했다. API와 Worker 모두 조회·업로드·삭제가 성공했다.

## 5. Kafka EC2 구축

### 5.1 서버 사양 확인과 조정

Kafka EC2의 초기 상태는 다음과 같았다.

```text
OS: Ubuntu 26.04 LTS
Java: 미설치
Private IP: 172.31.27.63
Memory: 약 1GiB
CPU: 2 vCPU
Disk: 28GiB 중 약 25GiB 사용 가능
```

1GiB에서는 Kafka JVM과 운영체제 Page Cache가 메모리를 경쟁하여 성능 수치를 왜곡할 수 있으므로 `t3.medium` 4GiB로 변경했다.

```text
Kafka EC2: t3.medium / 2 vCPU / 4GiB
Kafka Heap: 1GiB
```

EC2를 중지하고 Instance Type을 변경한 뒤 다시 시작했다. EBS의 Kafka 설치와 데이터는 유지됐고 사설 IP `172.31.27.63`도 유지됐다. Elastic IP가 없는 인스턴스는 중지·시작 후 공인 IP가 바뀔 수 있으므로 SSH 접속 주소를 다시 확인한다.

### 5.2 Java 17 설치

```bash
sudo apt update
sudo apt install openjdk-17-jre-headless -y
java -version
```

Kafka 4.x는 Java 17 이상을 요구한다.

### 5.3 Kafka 4.3.1 다운로드와 검증

```bash
cd /tmp
curl -fLO https://dlcdn.apache.org/kafka/4.3.1/kafka_2.13-4.3.1.tgz
curl -fLO https://downloads.apache.org/kafka/4.3.1/kafka_2.13-4.3.1.tgz.sha512
```

Apache의 `.sha512` 파일은 공백으로 나뉜 형식이라 `sha512sum -c`가 바로 처리하지 못했다. 다음과 같이 실제 값과 공식 값을 정규화하여 비교했다.

```bash
kafka_actual_sha512=$(sha512sum kafka_2.13-4.3.1.tgz | awk '{print toupper($1)}')
kafka_expected_sha512=$(sed '1s/^[^:]*:[[:space:]]*//' \
  kafka_2.13-4.3.1.tgz.sha512 | tr -d '[:space:]')

if [ "$kafka_actual_sha512" = "$kafka_expected_sha512" ]; then
  echo "Kafka checksum OK"
else
  echo "Kafka checksum FAILED"
fi
```

`Kafka checksum OK`를 확인한 뒤에만 설치했다.

### 5.4 Kafka 사용자와 디렉터리

```bash
sudo useradd \
  --system \
  --home /var/lib/kafka \
  --shell /usr/sbin/nologin \
  kafka

sudo tar -xzf /tmp/kafka_2.13-4.3.1.tgz -C /opt
sudo ln -s /opt/kafka_2.13-4.3.1 /opt/kafka

sudo install -d -o kafka -g kafka /var/lib/kafka/data
sudo install -d -o kafka -g kafka /var/log/kafka

/opt/kafka/bin/kafka-topics.sh --version
```

### 5.5 단일 KRaft Broker 설정

현재 환경은 단일 인스턴스가 Broker와 Controller 역할을 함께 수행한다.

```bash
sudo mkdir -p /etc/kafka
```

`/etc/kafka/server.properties`:

```properties
process.roles=broker,controller
node.id=1

controller.quorum.bootstrap.servers=127.0.0.1:9093
controller.listener.names=CONTROLLER

listeners=PLAINTEXT://0.0.0.0:9092,CONTROLLER://127.0.0.1:9093
advertised.listeners=PLAINTEXT://172.31.27.63:9092
listener.security.protocol.map=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
inter.broker.listener.name=PLAINTEXT

log.dirs=/var/lib/kafka/data
num.partitions=3

default.replication.factor=1
offsets.topic.replication.factor=1
transaction.state.log.replication.factor=1
transaction.state.log.min.isr=1
min.insync.replicas=1

auto.create.topics.enable=false
group.initial.rebalance.delay.ms=0

log.retention.hours=24
log.retention.check.interval.ms=300000
```

권한을 제한한다.

```bash
sudo chown root:kafka /etc/kafka/server.properties
sudo chmod 640 /etc/kafka/server.properties
```

Shell heredoc을 사용할 때 종료 문자열 `EOF` 앞에 공백이 있으면 Prompt가 `>`로 바뀌고 입력을 계속 기다린다. 이 경우 `Ctrl+C`로 취소하고 마지막 `EOF`를 줄의 첫 문자로 다시 입력한다.

### 5.6 KRaft Storage 초기화

```bash
kafka_cluster_id=$(/opt/kafka/bin/kafka-storage.sh random-uuid)
echo "$kafka_cluster_id"

sudo -u kafka /opt/kafka/bin/kafka-storage.sh format \
  --standalone \
  -t "$kafka_cluster_id" \
  -c /etc/kafka/server.properties

sudo -u kafka /opt/kafka/bin/kafka-storage.sh info \
  -c /etc/kafka/server.properties
```

`format`은 최초 한 번만 실행한다. Kafka가 데이터를 기록한 뒤 다시 실행하면 기존 데이터가 초기화될 수 있다.

### 5.7 Kafka systemd 서비스

`/etc/systemd/system/kafka.service`:

```ini
[Unit]
Description=Apache Kafka Server
Documentation=https://kafka.apache.org/
Wants=network-online.target
After=network-online.target

[Service]
Type=simple
User=kafka
Group=kafka
WorkingDirectory=/opt/kafka
Environment="KAFKA_HEAP_OPTS=-Xms1g -Xmx1g"
Environment="LOG_DIR=/var/log/kafka"
ExecStart=/opt/kafka/bin/kafka-server-start.sh /etc/kafka/server.properties
Restart=on-failure
RestartSec=5
KillSignal=SIGTERM
TimeoutStopSec=120
LimitNOFILE=100000
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable kafka
sudo systemctl start kafka
sudo systemctl status kafka --no-pager -l
sudo ss -ltnp | grep -E ':9092|:9093'
```

다음 두 Listener가 확인됐다.

```text
*:9092                 API·Worker 연결
127.0.0.1:9093         Kafka 내부 Controller
```

### 5.8 Topic과 DLT 생성

원본 Topic은 기본 보존 시간 24시간을 사용하고 DLT는 장애 조사와 수동 재처리를 위해 14일 보존한다.

```bash
/opt/kafka/bin/kafka-topics.sh \
  --create \
  --bootstrap-server 127.0.0.1:9092 \
  --topic media.encode.requested \
  --partitions 3 \
  --replication-factor 1

/opt/kafka/bin/kafka-topics.sh \
  --create \
  --bootstrap-server 127.0.0.1:9092 \
  --topic media.encode.requested.dlt \
  --partitions 3 \
  --replication-factor 1 \
  --config retention.ms=1209600000
```

```bash
/opt/kafka/bin/kafka-topics.sh \
  --list \
  --bootstrap-server 127.0.0.1:9092
```

확인된 Topic:

```text
media.encode.requested
media.encode.requested.dlt
```

Worker Retry Topic은 Worker 실행 시 Spring Kafka Admin이 관리하도록 두었다.

### 5.9 Kafka 보안 그룹과 연결 검증

Kafka 보안 그룹에는 다음 규칙만 둔다.

| Port | Source | Purpose |
|---:|---|---|
| 9092 | API EC2 Security Group | Producer 연결 |
| 9092 | Worker EC2 Security Group | Consumer 연결 |
| 22 | 개발자 공인 IP `/32` | 수동 SSH |

Controller `9093`은 외부에 개방하지 않는다.

API와 Worker EC2에서 각각 확인했다.

```bash
nc -zv -w 5 172.31.27.63 9092
```

두 서버 모두 `succeeded`가 출력됐다.

API 설정은 Port를 내부에서 붙이므로 다음처럼 IP만 전달한다.

```text
KAFKA_PRODUCER_IP=172.31.27.63
```

## 6. 성능 측정 EC2 크기 결정

초기 `t2.micro` 또는 `t3.micro` 1GiB 환경은 Spring Boot, Kafka, FFmpeg, k6와 Monitoring Agent가 실행될 때 메모리 부족 또는 CPU Credit 병목으로 결과를 왜곡할 가능성이 있었다.

현재 기준은 다음과 같다.

| Component | Instance 기준 | Runtime Memory 기준 | 상태 |
|---|---|---|---|
| API | `t3.medium`, 2 vCPU, 4GiB | JVM Heap 512MiB~1GiB | 변경·확인 완료 |
| Kafka | `t3.medium`, 2 vCPU, 4GiB | JVM Heap 1GiB | 변경·확인 완료 |
| Worker | `t3.medium`, 2 vCPU, 4GiB | JVM Heap 512MiB~1GiB + FFmpeg | 변경 권장, 배포 전 재확인 |
| k6·Monitoring | `t3.medium`, 2 vCPU, 4GiB | k6 + Prometheus + Grafana | 변경 권장, 설치 전 재확인 |

EC2 본체 외에도 RDS, EBS, 공인 IPv4와 Data Transfer 비용이 별도로 발생한다. 가격은 리전과 시점에 따라 달라지므로 측정 직전에 AWS Pricing 화면에서 확인하고, 사용하지 않는 인스턴스는 중지하거나 종료한다.

T3는 Burstable Instance이므로 장시간 CPU를 포화시키는 테스트에서는 CPU Credit을 함께 기록한다. FFmpeg 최대 처리 성능 자체를 정밀하게 비교하려면 후속 측정에서 Compute Optimized Instance 사용을 검토한다.

## 7. API EC2 실행 환경

### 7.1 서버 확인

API EC2에서 확인된 값은 다음과 같다.

```text
OS: Ubuntu 26.04 LTS
Java: OpenJDK 17
Instance: t3.medium
Memory: 약 3.7GiB
CPU: 2 vCPU
Private IP: 172.31.23.111
```

### 7.2 API 실행 사용자와 디렉터리

API Process를 SSH 사용자와 분리했다.

```bash
sudo useradd \
  --system \
  --home-dir /opt/onfilm-api \
  --shell /usr/sbin/nologin \
  onfilm-api

sudo install -d -o root -g root -m 755 /opt/onfilm-api
sudo install -d -o ubuntu -g ubuntu -m 755 /opt/onfilm-api/releases
```

- `ubuntu`: GitHub Actions가 SSH로 접속하여 Release JAR를 업로드한다.
- `onfilm-api`: systemd가 애플리케이션을 실행한다.
- `/opt/onfilm-api/current.jar`: 현재 Release를 가리키는 Symbolic Link다.

### 7.3 환경변수 파일

실제 비밀값은 저장소와 GitHub의 전체 YML Secret에 넣지 않고 API EC2의 `/etc/onfilm-api.env`에 둔다.

JWT Secret과 Callback Secret은 서로 다른 값을 사용한다.

```bash
openssl rand -hex 32
openssl rand -hex 32
```

Callback Secret은 Worker의 Callback 서명 설정에도 같은 값을 사용해야 한다.

`/etc/onfilm-api.env` Template:

```dotenv
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080

DB_URL=jdbc:mysql://RDS_ENDPOINT:3306/onfilm_api?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
DB_USER=onfilm_api_app
DB_PASSWORD="API_APP_PASSWORD"

KAFKA_PRODUCER_IP=172.31.27.63

S3_BUCKET=onfilm-s3-bucket
S3_REGION=ap-northeast-2
S3_PUBLIC_BASE_URL=https://onfilm-s3-bucket.s3.ap-northeast-2.amazonaws.com

JWT_SECRET="GENERATED_JWT_SECRET"
MEDIA_ENCODE_CALLBACK_SECRET="SHARED_CALLBACK_SECRET"

REFRESH_TOKEN_RETENTION=30d
REFRESH_TOKEN_CLEANUP_CRON="0 0 4 * * *"
MEDIA_ENCODE_JOB_TIMEOUT=PT4H30M
MEDIA_ENCODE_OUTBOX_RETENTION=P7D
MEDIA_ENCODE_JOB_RETENTION=P30D

MANAGEMENT_SERVER_ADDRESS=0.0.0.0
MANAGEMENT_SERVER_PORT=8081

JAVA_OPTS=-Xms512m -Xmx1g
```

위 보존 기간과 제한시간은 현재 `application-prod.yml`의 기본값과 같다. 환경별로 값을 변경할 때는 단위를 포함한 Spring `Duration` 형식과 관련 장애 대응 정책을 함께 확인한다.

다음 값은 설정하지 않는다.

```text
S3_ACCESS_KEY
S3_SECRET_KEY
```

파일 접근 권한을 제한한다.

```bash
sudo chown root:onfilm-api /etc/onfilm-api.env
sudo chmod 640 /etc/onfilm-api.env
sudo stat -c '%U %G %a %n' /etc/onfilm-api.env
```

비밀값을 출력하지 않고 변수 이름만 확인하려면 다음 명령을 사용한다.

```bash
sudo sed -E 's/=.*/=<configured>/' /etc/onfilm-api.env
```

### 7.4 API systemd 서비스

`/etc/systemd/system/onfilm-api.service`:

```ini
[Unit]
Description=OnFilm API
Wants=network-online.target
After=network-online.target

[Service]
Type=simple
User=onfilm-api
Group=onfilm-api
WorkingDirectory=/opt/onfilm-api
EnvironmentFile=/etc/onfilm-api.env
ExecStart=/usr/bin/java $JAVA_OPTS -jar /opt/onfilm-api/current.jar
Restart=on-failure
RestartSec=5
KillSignal=SIGTERM
TimeoutStartSec=180
TimeoutStopSec=60
SuccessExitStatus=143
UMask=0027

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemd-analyze verify /etc/systemd/system/onfilm-api.service
```

Ubuntu 26.04에서 다음 XFS 관련 경고가 함께 출력됐지만 OnFilm 서비스 오류가 아니므로 무시했다.

```text
Support for option CPUAccounting= has been removed and it is ignored
```

`current.jar`가 배포되기 전에는 서비스를 시작하지 않았다.

## 8. 저장소 설정과 배포 JAR 정리

### 8.1 YML과 환경변수 정책

기존에는 `src/main/resources/application.yml`을 Git에서 제외하고 GitHub `APPLICATION_YML` Secret으로 파일 전체를 만들었다. 다음 구조로 변경했다.

```text
Git 추적
├── application.yml          공통 구조, JWT_SECRET 필수
├── application-dev.yml      개발용 안전한 기본값
└── application-prod.yml     운영 환경변수 계약

Git 제외
├── application-local.yml
├── application-secret.yml
└── .env / .env.*
```

운영 JWT 설정은 실제 값을 저장하지 않고 다음 Placeholder를 사용한다.

```yaml
jwt:
  secret: ${JWT_SECRET}
```

GitHub Actions에 전체 `APPLICATION_YML`을 저장하지 않으며 Runtime Secret은 EC2의 `/etc/onfilm-api.env`가 제공한다.

### 8.2 로컬 영상의 JAR 포함 방지

`src/main/resources/static/videos` 아래의 로컬 영상이 Gradle Resource에 포함돼 로컬 JAR가 약 1.2GiB까지 증가한 문제를 확인했다.

`.gitignore`에 디렉터리를 명시하고 Gradle `processResources`에서도 제외했다.

```groovy
tasks.named('processResources') {
    exclude 'static/videos/**'
}
```

검증 결과:

```text
변경 전 JAR: 약 1.2GiB
변경 후 JAR: 약 97MiB
BOOT-INF/classes/static/videos/: 없음
```

실제 Media는 S3 Object로 관리하며 애플리케이션 JAR에 포함하지 않는다.

## 9. GitHub Actions API 배포

### 9.1 배포 흐름 변경

기존 CodeDeploy·S3 Bundle·`APPLICATION_YML` 생성 Workflow와 테스트 Branch용 구형 SCP Workflow를 제거했다.

현재 [API CI Workflow](../../.github/workflows/api-ci.yml)는 다음 순서로 실행된다.

```text
Unit tests ───────────────┐
                         ├──▶ Deploy API to performance EC2
MySQL integration tests ─┘
```

배포 Job은 다음 조건을 모두 만족할 때만 실행한다.

- Event가 `push`
- Branch가 `main`
- 단위 테스트 성공
- MySQL Testcontainers 통합 테스트 성공

배포 절차:

1. 실행 가능한 Spring Boot JAR를 생성한다.
2. 커밋 SHA를 포함한 이름으로 API EC2의 `/opt/onfilm-api/releases`에 SCP 전송한다.
3. `/opt/onfilm-api/current.jar` Symbolic Link를 새 Release로 변경한다.
4. `onfilm-api` systemd 서비스를 재시작한다.
5. 최대 3분 동안 `127.0.0.1:8081/actuator/health`가 `UP`인지 확인한다.
6. Health 검사 실패 시 최근 Journal Log를 출력하고 이전 JAR로 Symbolic Link를 되돌린다.

### 9.2 GitHub Environment Variables

GitHub Repository에 `performance` Environment를 만들고 다음 Variables를 등록했다.

```text
API_EC2_HOST=API_EC2_ELASTIC_IP
API_EC2_USER=ubuntu
API_DEPLOY_DIR=/opt/onfilm-api/releases
```

`API_EC2_HOST`에는 `172.31.23.111` 같은 사설 IP가 아니라 인터넷에서 GitHub-hosted Runner가 접근할 수 있는 API 탄력적 IP를 넣는다.

### 9.3 GitHub Environment Secrets

```text
API_EC2_SSH_PRIVATE_KEY
API_EC2_KNOWN_HOSTS
```

`API_EC2_SSH_PRIVATE_KEY`에는 API EC2 접속용 PEM 전체를 저장한다. BEGIN·END 행을 포함하며 채팅이나 저장소에는 기록하지 않는다.

Host Key는 서버와 Mac에서 지문을 비교한 뒤 등록했다.

API EC2에서 원본 지문 확인:

```bash
sudo ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub
```

Mac에서 탄력적 IP의 공개키 수집:

```bash
ssh-keyscan -T 10 -t ed25519 API_EC2_ELASTIC_IP \
  2>/dev/null > /tmp/onfilm-api-known-hosts
```

Known Hosts 행은 `hostname key-type key` 형식이므로 지문 비교 시 Hostname 필드를 제외한다.

```bash
awk '{print $2, $3}' /tmp/onfilm-api-known-hosts | ssh-keygen -lf -
```

두 `SHA256:...` 지문이 같으면 다음 명령의 한 줄 전체를 `API_EC2_KNOWN_HOSTS`로 등록한다.

```bash
cat /tmp/onfilm-api-known-hosts
```

등록하는 실제 형식은 다음과 같다.

```text
API_EC2_ELASTIC_IP ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAA...
```

`SHA256:...`는 비교용 지문이며 Secret에 넣는 Known Hosts 원문이 아니다. Key Comment의 `root@...`와 `no comment` 차이는 인증에 영향을 주지 않는다.

### 9.4 사설 IP와 탄력적 IP 구분

EC2에서 `hostname -I`로 보이는 주소는 사설 IP다. 탄력적 IP는 AWS Network 계층에서 사설 IP에 Mapping되므로 EC2 운영체제에는 직접 표시되지 않는다.

```text
Mac·GitHub Actions → API Elastic IP → 172.31.23.111
Worker·k6          → API Private IP  → 172.31.23.111
```

- Mac SSH, `ssh-keyscan`, `API_EC2_HOST`: 탄력적 IP
- Worker Callback, k6 내부 호출: 사설 IP

### 9.5 GitHub Runner용 임시 SSH 규칙

GitHub-hosted Runner의 출발지 IP가 고정되지 않기 때문에 단순 SCP 배포를 위해 API EC2의 `22` 포트를 임시로 열었다.

먼저 API EC2의 SSH 정책을 확인했다.

```bash
sudo sshd -T | grep -E \
  'passwordauthentication|permitrootlogin|pubkeyauthentication'
```

확인 결과:

```text
permitrootlogin prohibit-password
pubkeyauthentication yes
passwordauthentication no
```

API 보안 그룹에는 개인 접속 규칙을 유지하면서 GitHub Actions용 규칙을 별도로 추가했다.

| Type | Port | Source | Description |
|---|---:|---|---|
| SSH | 22 | 개발자 공인 IP `/32` | personal SSH access |
| SSH | 22 | `0.0.0.0/0` | temporary GitHub Actions deployment |

기존 개인 IP 규칙을 남겨둔 이유는 성능 측정 종료 후 임시 규칙만 명확하게 제거하기 위해서다.

이 구성은 Key 인증만 허용하더라도 인터넷에 SSH Port를 노출하는 절충안이다. 장기 운영에서는 GitHub OIDC로 실행 시점의 Runner IP만 허용하거나 SSM, Self-hosted Runner, Private Networking 같은 방식으로 전환한다.

### 9.6 첫 배포 결과

`main` Push 후 다음 세 Job이 모두 통과했다.

```text
Unit tests
MySQL integration tests
Deploy API to performance EC2
```

배포 Job의 Actuator Health 검사도 성공했으므로 API Process 시작, RDS Migration·연결과 Management Port 응답까지 확인됐다.

## 10. API EC2 배포 파일과 로그 위치

GitHub Actions는 EC2에서 `git pull`하지 않는다. GitHub-hosted Runner에서 빌드한 JAR만 전송하므로 `/home/ubuntu`에서 `ls`를 실행했을 때 파일이 없어도 정상이다.

```text
/opt/onfilm-api
├── current.jar -> releases/onfilm-api-COMMIT_SHA.jar
└── releases
    └── onfilm-api-COMMIT_SHA.jar

/etc/onfilm-api.env
/etc/systemd/system/onfilm-api.service
```

확인 명령:

```bash
sudo systemctl is-active onfilm-api
sudo systemctl is-enabled onfilm-api
readlink -f /opt/onfilm-api/current.jar
curl -fsS http://127.0.0.1:8081/actuator/health
echo
```

로그는 별도 `output.log`가 아니라 systemd Journal에서 확인한다.

```bash
sudo journalctl -u onfilm-api --since "10 minutes ago" --no-pager
sudo journalctl -u onfilm-api -f
```

## 11. 보안 그룹 최종 점검 기준

현재 성능 측정 흐름에 필요한 핵심 규칙은 다음과 같다.

### API Security Group

| Port | Source | Purpose |
|---:|---|---|
| 22 | 개발자 공인 IP `/32` | 개인 SSH |
| 22 | `0.0.0.0/0` | 임시 GitHub Actions 배포 |
| 8080 | k6 Security Group | 부하 테스트 |
| 8080 | Worker Security Group | Callback |
| 8081 | k6·Monitoring Security Group | Actuator·Prometheus |

### Worker Security Group

| Port | Source | Purpose |
|---:|---|---|
| 22 | 개발자 공인 IP `/32` | 개인 SSH |
| 8082 | k6·Monitoring Security Group | Actuator·Prometheus |

Worker GitHub Actions 배포를 구성할 때만 API와 같은 임시 SSH 규칙을 별도로 검토한다.

### Kafka Security Group

| Port | Source | Purpose |
|---:|---|---|
| 22 | 개발자 공인 IP `/32` | 개인 SSH |
| 9092 | API Security Group | Producer |
| 9092 | Worker Security Group | Consumer |

### k6 Security Group

| Port | Source | Purpose |
|---:|---|---|
| 22 | 개발자 공인 IP `/32` | 개인 SSH |
| 3000 | 개발자 공인 IP `/32` | Grafana |
| 9090 | 개발자 공인 IP `/32` | Prometheus UI, 필요할 때만 |

### RDS Security Group

| Port | Source | Purpose |
|---:|---|---|
| 3306 | API Security Group | API DB |
| 3306 | Worker Security Group | Worker DB |

RDS에는 `22`, Public Access와 `0.0.0.0/0:3306`을 허용하지 않는다.

## 12. 다음 작업 순서

현재 완료 지점 이후에는 다음 순서로 진행한다.

1. Worker EC2의 Instance Type, 메모리, Java와 ffmpeg 상태를 확인한다.
2. Worker 전용 실행 사용자와 `/opt/onfilm-worker/releases`를 만든다.
3. `/etc/onfilm-worker.env`에 Worker DB, Kafka, S3, API Callback과 공유 HMAC Secret을 설정한다.
4. Worker systemd 서비스를 등록한다.
5. Worker 저장소의 CI 성공 후 SCP·Health 확인 배포를 구성한다.
6. 실제 Media 요청을 한 건 발행하여 Outbox → Kafka → Worker Inbox → S3 → Callback 전체 흐름을 확인한다.
7. k6 EC2에 k6, Prometheus와 Grafana를 구성한다.
8. Test Data와 측정 시나리오를 고정한다.
9. Warm-up과 본 측정을 분리하고 API·RDS·Kafka·Worker·k6 지표를 함께 기록한다.
10. 결과와 병목, 개선 전후 수치를 문서화한다.
11. 성능 측정 종료 후 아래 정리 Checklist를 실행한다.

## 13. 종료 시 정리 Checklist

- [ ] API·Worker 보안 그룹의 임시 `0.0.0.0/0:22` 규칙 제거
- [ ] 불필요하게 발급한 IAM User Access Key 비활성화·삭제
- [ ] 성능 측정용 EC2 종료가 아니라 필요 여부 확인 후 Terminate
- [ ] 연결 해제한 Elastic IP Release
- [ ] RDS Snapshot 필요 여부 확인 후 DB Instance 삭제
- [ ] 삭제 방지와 최종 Snapshot 옵션 확인
- [ ] 불필요한 EBS Volume과 Snapshot 삭제
- [ ] S3 Test Object와 불완전 Multipart Upload 정리
- [ ] CloudWatch Log·Metric 보존 필요 여부 확인
- [ ] Cost Explorer와 Billing에서 잔여 과금 Resource 확인
- [ ] 실제 Secret과 비밀번호 Rotation 또는 폐기

## 14. 참고 자료

- [AWS SDK for Java - EC2 IAM Role Credentials](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/ec2-iam-roles.html)
- [AWS S3 Security Best Practices](https://docs.aws.amazon.com/AmazonS3/latest/userguide/security-best-practices.html)
- [AWS RDS Password Management with Secrets Manager](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/rds-secrets-manager.html)
- [Apache Kafka Downloads](https://kafka.apache.org/community/downloads/)
- [Apache Kafka Quick Start](https://kafka.apache.org/quickstart/)
- [Apache Kafka KRaft](https://kafka.apache.org/42/operations/kraft/)
- [GitHub Actions Variables](https://docs.github.com/en/actions/concepts/workflows-and-actions/variables)
- [GitHub Actions Secrets](https://docs.github.com/en/actions/reference/security/secrets)
- [GitHub-hosted Runner Private Networking](https://docs.github.com/en/actions/concepts/runners/private-networking)
