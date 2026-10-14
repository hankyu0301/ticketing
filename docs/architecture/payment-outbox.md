# 결제 Outbox

비동기 결제 요청은 결제와 `payment_outbox`를 같은 트랜잭션에서 저장한다.
HTTP 202는 DB 접수 완료를 의미한다. RabbitMQ 발행 성공이나 결제 완료 응답은 아니다.

## 실행과 트랜잭션

- `PaymentOutboxWriter.append`: 요청 트랜잭션에 반드시 참여한다(MANDATORY).
- `PaymentOutboxScheduler`: 500ms마다 전용 Executor에 작업을 제출한다.
- 전용 Worker: 기본 4개, 대기 큐 없음. 실행 후 DB 선점하므로 제출 거절 시 메시지를 선점하지 않는다.
- `PaymentOutboxStateService`: 선점과 결과 기록을 별도 트랜잭션(REQUIRES_NEW)으로 수행한다.
- RabbitMQ 호출 중에는 DB 트랜잭션을 유지하지 않는다. Worker가 confirm을 최대 5초 기다린다.
- ACK이며 반환 메시지가 없을 때만 PUBLISHED로 기록한다. 메시지는 persistent, 큐는 durable이다.

`eventId`는 재발행에도 유지한다. `attemptId`는 결제 생성 및 FAILED 결제 재사용 때 바뀐다.
`claimToken`은 발행 선점마다 바뀌며, 모든 발행 결과 갱신은 해당 토큰을 조건으로 한다.
소비자는 현재 attemptId와 PENDING 상태를 잠금 안에서 검사해 중복 및 이전 시도를 무시한다.

## 실패와 복구

READY → PROCESSING → PUBLISHED. 발행 실패는 READY로 되돌리고 DB의 available_at 이후 재시도한다.
재시도 간격은 1초부터 지수 증가하며 최대 60초, 80~100% jitter를 적용한다.
최대 10회 선점 후 실패하면 PARKED로 보존한다. 서버 종료로 만료된 선점도 횟수를 유지한다.
발행 성공 후 DB 기록 실패 시 PROCESSING이 남으며, 기본 60초 선점 만료 후 재발행할 수 있다.
이때 소비자 멱등성이 중복 결제 완료를 막는다. PARKED는 결제 FAILED를 의미하지 않는다.

연결·채널 확보·채널 RPC 대기는 각각 5초로 설정한다. confirm timeout은 전체 발행 시간 제한이 아니다.
지연이나 프로세스 중단으로 선점이 만료되면 중복 전달이 가능하므로 정확히 한 번 전달을 보장하지 않는다.
종료 시 제출을 중단하고 Worker 종료를 최대 30초 기다린다. 미완료 선점은 다음 실행에서 복구한다.

## 실행 제어

`ticketing.scheduler.enabled`와 `ticketing.payment.outbox.enabled`가 모두 true일 때 자동 발행한다.
Outbox 설정은 `ticketing.payment.outbox` 아래에서 변경한다. 테스트·부하테스트는 자동 발행을 끈다.
실행 플래그를 false로 바꾸고 재시작하면 발행을 중단한다. 요청이 계속되면 READY가 누적된다.
실 데이터가 없는 초기 스키마를 기준으로 하며 기존 메시지 호환 및 데이터 migration은 제공하지 않는다.

## 상태 확인

```sql
SELECT status, COUNT(*) FROM payment_outbox GROUP BY status;

SELECT event_id, payment_id, attempt_id, attempt_count, available_at, last_error
FROM payment_outbox
WHERE status IN ('READY', 'PARKED')
ORDER BY available_at, id LIMIT 100;

SELECT event_id, payment_id, attempt_id, locked_until
FROM payment_outbox
WHERE status = 'PROCESSING' AND locked_until <= CURRENT_TIMESTAMP(6)
ORDER BY locked_until, id LIMIT 100;
```

로그는 eventId, paymentId, attemptId로 추적한다. PARKED 전환은 ERROR로 기록한다.
자동 삭제·관리자 API·소비자 DLQ는 이번 범위에 포함하지 않는다. 데이터 보관 기간 정책은 별도 작업이다.

## PARKED 재처리

원인을 해결한 뒤 대상 이벤트를 확인하고 아래 SQL의 UUID를 해당 event_id로 치환해 실행한다.
eventId, attemptId, payload는 변경하지 않는다. 이미 완료된 결제나 이전 시도는 소비자가 무시한다.
예약이 만료됐다면 재발행해도 기존 정책상 결제를 완료할 수 없다.

```sql
UPDATE payment_outbox
SET status = 'READY', attempt_count = 0, available_at = CURRENT_TIMESTAMP(6),
    claim_token = NULL, locked_until = NULL, last_error = NULL,
    updated_at = CURRENT_TIMESTAMP(6)
WHERE event_id = 'REPLACE_WITH_EVENT_UUID' AND status = 'PARKED';
```

## 검증

관련 단위 테스트부터 실행하고 MariaDB/RabbitMQ Testcontainers로 저장 원자성, 선점 경쟁,
선점 만료·토큰 교체, 발행 결과 저장 실패 후 중복 소비, 라우팅 실패를 검증한다.
테스트는 자동 폴링 대신 Relay를 명시적으로 실행한다. 전체 회귀 명령은 `./gradlew.bat test`다.
