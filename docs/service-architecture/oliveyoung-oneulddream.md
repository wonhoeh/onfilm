# 올리브영 오늘드림 아키텍처 분석

> 공개된 정보와 기술 블로그 기반 추론. 내부 구조는 다를 수 있음.

---

## 서비스 개요

- **오늘드림**: 주문 후 3시간 내 배송 서비스
- 사용자 위치 기반으로 가장 가까운 매장에서 피킹 후 배송
- 올영세일 등 트래픽 폭발 구간에서도 주문/재고 정합성 보장이 핵심

---

## 핵심 문제

1. 동시 주문 시 재고 오버셀링 방지
2. 주문 → 결제 → 재고 → 배송 순서 보장
3. 중간 단계 실패 시 전체 롤백
4. 피크 트래픽(올영세일) 대응

---

## 주문 처리 흐름 (Kafka 기반 비동기)

```
1. 사용자 결제 버튼 클릭
2. 주문 생성 → DB에 PENDING 상태 저장
3. Kafka에 "order.created" 이벤트 발행
         ↓
4. 결제 서비스 consume → 결제 처리
5. 결제 완료 → "payment.completed" 이벤트 발행
         ↓
6. 재고 서비스 consume → 재고 차감
7. 재고 차감 완료 → "stock.deducted" 이벤트 발행
         ↓
8. 배송 서비스 consume → 배송 매장 배정 → 배송 시작
```

각 단계가 성공하면 다음 단계로 이벤트를 넘기는 구조.

---

## Kafka 토픽 구성

| 토픽 | 발행자 | 소비자 |
|---|---|---|
| `order.created` | 주문 서비스 | 결제 서비스 |
| `payment.completed` | 결제 서비스 | 재고 서비스 |
| `stock.deducted` | 재고 서비스 | 배송 서비스 |
| `payment.cancelled` | 재고 서비스 (실패 시) | 결제 서비스 |
| `order.failed` | 각 서비스 (실패 시) | 주문 서비스 |

---

## Saga 패턴 (분산 트랜잭션 관리)

### 개념
분산 환경에서 여러 단계로 나뉜 트랜잭션이 중간에 실패했을 때,
앞서 완료된 단계들을 순서대로 되돌리는 패턴.

일반 DB 트랜잭션은 `rollback` 한 번으로 되돌아가지만,
서비스가 분리된 환경에서는 각 서비스가 직접 보상 처리를 해야 함.

### 실패 시나리오 예시

**결제 성공 → 재고 부족 실패**
```
재고 차감 실패
→ "stock.failed" 이벤트 발행
→ 결제 서비스 consume → 결제 취소(환불) 처리
→ 주문 상태 FAILED로 변경
→ 사용자에게 실패 알림
```

### 보상 트랜잭션
각 단계가 실패했을 때 이전 단계를 되돌리는 로직.

| 실패 단계 | 보상 처리 |
|---|---|
| 결제 실패 | 주문 FAILED 처리 |
| 재고 차감 실패 | 결제 취소(환불) |
| 배송 배정 실패 | 재고 복구 + 결제 취소 |

---

## 재고 정합성 (동시성 방어)

올영세일 같은 피크 트래픽에서 동시 주문이 몰릴 때 오버셀링 방지.

```
Redis에 재고 수량 관리
→ 주문 요청 시 Redis에서 원자적 차감 (DECR)
→ 재고 0 이하면 즉시 품절 처리
→ DB는 최종 정합성 보장용으로 사용
```

- Redis DECR 연산은 원자적으로 동작 → Race Condition 방지
- DB까지 매번 접근하지 않아 응답 속도 유지

---

## MSA 구조

서비스별로 독립적인 DB를 가지는 구조로 추정.

```
주문 서비스  → 주문 DB  (orders, order_items)
결제 서비스  → 결제 DB  (payments, payment_history)
재고 서비스  → 재고 DB  (stocks, stock_history)
배송 서비스  → 배송 DB  (deliveries, tracking)
```

### 조회 문제
주문 내역 화면에 주문 + 결제 + 배송 정보가 한 번에 필요한데,
DB가 분리되어 있어 JOIN 불가.

**해결 방법**
- 각 서비스 API를 따로 호출 후 합치기 (API Composition)
- Kafka 이벤트로 조회용 DB에 따로 저장 (CQRS 패턴)

---

## onfilm과의 연결

onfilm에서 구현한 Kafka 파이프라인과 구조가 동일.

```
onfilm:
upload.completed → 인코딩 워커 consume → ffmpeg 처리 → callback

오늘드림:
order.created → 결제 서비스 consume → 결제 처리 → 다음 이벤트 발행
```

차이점은 단계 수와 실패 시 보상 트랜잭션(Saga) 유무.

---

## 참고 키워드

- Saga 패턴 (Choreography 방식 vs Orchestration 방식)
- CQRS (Command Query Responsibility Segregation)
- Redis 원자적 연산 (DECR)
- Kafka at-least-once + 멱등성 처리
