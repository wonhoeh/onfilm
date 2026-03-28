# MediaEncodeJob 중복 callback 처리 리뷰

## 1. 먼저 알아야 할 Kafka 개념

### at-least-once 전달이란?

Kafka는 메시지를 "최소 한 번은 전달한다"고 보장해요.

> "최소 한 번" = 한 번 이상 올 수 있다 = 같은 메시지가 두 번 올 수도 있다

왜 두 번 올까요? 예를 들면:

```
worker → 인코딩 완료 → callback API 호출(DONE)
→ 응답을 받기 전에 네트워크가 끊김
→ worker는 "실패한 것 같으니 다시 보내야지"
→ callback API 또 호출(DONE)
```

API 서버에서는 이미 DONE으로 처리했는데, worker 입장에서는 확인을 못 했으니 또 보내는 거예요. 이건 버그가 아니라 분산 시스템에서 예상되는 상황이에요.

### 멱등성(Idempotency)이란?

같은 요청을 여러 번 보내도 결과가 달라지지 않는 성질이에요.

```
// 멱등성 O
DELETE /orders/1  → 삭제됨
DELETE /orders/1  → 이미 없음, 문제 없음

// 멱등성 X
POST /orders/1/cancel  → 취소됨
POST /orders/1/cancel  → 또 취소? 오류?
```

Kafka at-least-once 환경에서 callback 처리는 멱등성을 갖춰야 해요. 같은 callback이 두 번 와도 안전하게 처리되어야 하거든요.

### terminal 상태란?

더 이상 다른 상태로 전이되지 않는 최종 상태예요.

```
REQUESTED → PROCESSING → DONE   (terminal)
                       → FAILED (terminal)
```

DONE과 FAILED는 한번 도달하면 더 이상 바뀌지 않는 최종 상태예요.

---

## 2. 현재 프로젝트 코드가 어떻게 되어있나

### 상태 정의 (MediaEncodeJobStatus.java)

```java
public enum MediaEncodeJobStatus {
    REQUESTED,   // 인코딩 요청됨 (Kafka 발행 직후)
    PROCESSING,  // worker가 인코딩 시작
    DONE,        // 인코딩 완료
    FAILED       // 인코딩 실패
}
```

### 상태 전이 메서드 (MediaEncodeJob.java)

```java
public void markProcessing(Instant startedAt) {
    if (this.status != MediaEncodeJobStatus.REQUESTED) {
        throw new IllegalStateException("INVALID_MEDIA_JOB_STATUS_TRANSITION");
    }
    this.status = MediaEncodeJobStatus.PROCESSING;
    ...
}

public void markDone(Instant completedAt) {
    if (this.status != MediaEncodeJobStatus.PROCESSING) {
        throw new IllegalStateException("INVALID_MEDIA_JOB_STATUS_TRANSITION");
    }
    this.status = MediaEncodeJobStatus.DONE;
    ...
}

public void markFailed(String failureReason, Instant completedAt) {
    if (this.status != MediaEncodeJobStatus.REQUESTED && this.status != MediaEncodeJobStatus.PROCESSING) {
        throw new IllegalStateException("INVALID_MEDIA_JOB_STATUS_TRANSITION");
    }
    this.status = MediaEncodeJobStatus.FAILED;
    ...
}
```

### 현재 흐름 정리

| 상황 | 현재 동작 |
|---|---|
| REQUESTED → PROCESSING | 정상 처리 |
| PROCESSING → DONE | 정상 처리 |
| PROCESSING → FAILED | 정상 처리 |
| DONE → DONE (중복 callback) | **예외 발생 (IllegalStateException)** |
| FAILED → FAILED (중복 callback) | **예외 발생 (IllegalStateException)** |
| DONE → FAILED (중복 callback) | **예외 발생 (IllegalStateException)** |

### 문제가 되는 흐름

```
1. worker → DONE callback 전송
2. API 서버 → DONE 처리 성공
3. 응답이 worker에게 전달되기 전 네트워크 끊김
4. worker → "실패한 것 같으니 재시도" → DONE callback 다시 전송
5. API 서버 → 이미 DONE 상태인데 markDone() 호출 → IllegalStateException 발생
6. API 서버 → 500 응답 반환
7. worker → "또 실패했네, 다시 시도" → 무한 반복 가능
```

예외를 던지면 worker 입장에서는 "실패"로 인식하고 재시도해요. 재시도하면 또 예외가 나오는 악순환이 생겨요.

---

## 3. 어떻게 수정해야 하나

### 핵심 아이디어

terminal 상태(DONE, FAILED)에 이미 도달했으면 중복 callback을 조용히 무시하면 돼요. 예외를 던지는 게 아니라 그냥 return 하는 거예요.

### 수정 대상 파일

`src/main/java/com/onfilm/domain/kafka/entity/MediaEncodeJob.java`

### markDone() 수정

```java
// 현재
public void markDone(Instant completedAt) {
    if (this.status != MediaEncodeJobStatus.PROCESSING) {
        throw new IllegalStateException("INVALID_MEDIA_JOB_STATUS_TRANSITION");
    }
    this.status = MediaEncodeJobStatus.DONE;
    this.completedAt = completedAt;
    this.failureReason = null;
}

// 수정 후
public void markDone(Instant completedAt) {
    if (this.status == MediaEncodeJobStatus.DONE) {
        return; // 이미 완료 상태면 중복 callback 무시
    }
    if (this.status != MediaEncodeJobStatus.PROCESSING) {
        throw new IllegalStateException("INVALID_MEDIA_JOB_STATUS_TRANSITION");
    }
    this.status = MediaEncodeJobStatus.DONE;
    this.completedAt = completedAt;
    this.failureReason = null;
}
```

### markFailed() 수정

```java
// 현재
public void markFailed(String failureReason, Instant completedAt) {
    if (this.status != MediaEncodeJobStatus.REQUESTED && this.status != MediaEncodeJobStatus.PROCESSING) {
        throw new IllegalStateException("INVALID_MEDIA_JOB_STATUS_TRANSITION");
    }
    this.status = MediaEncodeJobStatus.FAILED;
    this.completedAt = completedAt;
    this.failureReason = failureReason;
}

// 수정 후
public void markFailed(String failureReason, Instant completedAt) {
    if (this.status == MediaEncodeJobStatus.FAILED) {
        return; // 이미 실패 상태면 중복 callback 무시
    }
    if (this.status != MediaEncodeJobStatus.REQUESTED && this.status != MediaEncodeJobStatus.PROCESSING) {
        throw new IllegalStateException("INVALID_MEDIA_JOB_STATUS_TRANSITION");
    }
    this.status = MediaEncodeJobStatus.FAILED;
    this.completedAt = completedAt;
    this.failureReason = failureReason;
}
```

### markProcessing()은 수정 안 해도 되나?

```java
public void markProcessing(Instant startedAt) {
    if (this.status != MediaEncodeJobStatus.REQUESTED) {
        throw new IllegalStateException("INVALID_MEDIA_JOB_STATUS_TRANSITION");
    }
    ...
}
```

PROCESSING은 terminal 상태가 아니에요. 중복 PROCESSING callback은 실제로 이상한 상황이에요 (worker가 같은 job을 두 번 시작하는 것). 이건 예외를 던져서 명확히 오류로 처리하는 게 맞아요.

### 수정 후 흐름 정리

| 상황 | 수정 후 동작 |
|---|---|
| REQUESTED → PROCESSING | 정상 처리 |
| PROCESSING → DONE | 정상 처리 |
| PROCESSING → FAILED | 정상 처리 |
| DONE → DONE (중복 callback) | **조용히 무시 (return)** |
| FAILED → FAILED (중복 callback) | **조용히 무시 (return)** |
| DONE → FAILED (중복 callback) | 예외 발생 (이건 진짜 이상한 상황) |
| PROCESSING → PROCESSING (중복) | 예외 발생 (이건 진짜 이상한 상황) |

---

## 4. 수정 전후 비교 요약

```
[수정 전]
중복 DONE callback → IllegalStateException → worker 재시도 → 또 예외 → 무한반복

[수정 후]
중복 DONE callback → 조용히 무시(return) → worker에게 200 응답 → 재시도 없음
```

---

## 5. 면접에서 어떻게 설명하면 좋은가

> "Kafka는 at-least-once 전달 방식이라 같은 callback이 두 번 올 수 있습니다. 그래서 이미 DONE이나 FAILED 같은 terminal 상태에 도달한 job은 중복 callback을 조용히 무시해서 멱등성을 확보했습니다. 예외를 던지면 worker가 실패로 인식하고 재시도해 오히려 더 많은 중복 callback이 발생하는 문제가 생기거든요."
