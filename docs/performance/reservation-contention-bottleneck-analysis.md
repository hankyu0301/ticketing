# 예약 좌석 경합 병목 탐색 결과

> 이 문서는 2026-06-10~11의 빠른 실패 적용 전 병목 진단 기록입니다. 이후 Redis 예약 불가 캐시와 `200ms` lock wait를 적용한 결과는 [빠른 실패 및 lock wait 튜닝](./reservation-fast-fail-loadtest-result.md), admission 결과는 [Active admission 기준](./active-admission-loadtest-result.md)에서 확인할 수 있습니다. 최신 실행 방법은 [loadtest 가이드](../../loadtest/README.md)를 기준으로 합니다.

## 1. 목적

이번 문서는 좌석 경합 부하테스트에서 관측되는 지연이 어디에서 발생하는지 좁히기 위한 실험 기록이다.

앞선 인기 좌석군 경합 테스트에서는 중복 예약이 발생하지 않는다는 점은 확인했다. 하지만 전체 `http_req_duration`만으로는 다음 질문에 답하기 어려웠다.

- 예약 성공 요청이 실제로 더 느린가?
- 빠른 400 비즈니스 실패가 전체 p95를 희석하고 있는가?
- 진짜 row lock 경합을 만들면 DB lock wait가 관측되는가?
- 지연이 DB lock인지, commit/write인지, 애플리케이션 요청 대기인지 어떻게 구분할 수 있는가?

그래서 이번 단계에서는 두 가지를 추가했다.

1. 예약 응답 duration을 성공 201과 비즈니스 실패 400으로 분리
2. 단일 좌석으로 요청을 몰아넣는 burst 경합 시나리오 추가

## 2. 적용 변경

운영 Java 코드는 변경하지 않았다. 변경은 `loadtest/k6` 하위에만 제한했다.

### 2.1 성공/실패 duration metric

기존에는 예약 결과 비율만 기록했다.

- `reservation_success_rate`
- `reservation_business_failure_rate`
- `reservation_unexpected_failure_rate`

이번에 다음 Trend metric을 추가했다.

- `reservation_duration_ms`
- `reservation_success_duration_ms`
- `reservation_business_failure_duration_ms`
- `reservation_unexpected_failure_duration_ms`

이제 전체 p95뿐 아니라 성공 예약과 실패 예약의 p95를 따로 볼 수 있다.

### 2.2 burst 시나리오

신규 시나리오:

- `burst-reservation-contention`

특징:

- setup 단계에서 VU별 loadtest 세션을 미리 발급한다.
- run 단계에서는 예약 요청만 보낸다.
- `per-vu-iterations` executor로 각 VU가 짧은 시간에 1회씩 요청한다.
- `reservation.burstSeats.count`를 1, 10, 100으로 바꾸면 단일 좌석 또는 좁은 좌석군 경합을 만들 수 있다.

대표 target:

- `loadtest/k6/data/targets.burst-contention.json`

## 3. 공통 실행 환경

| 항목 | 값 |
| --- | --- |
| 실행일 | 2026-06-10, 2026-06-11 |
| 실행 환경 | 로컬 Windows 개발 PC |
| 대상 profile | `loadtest` |
| Spring Boot | `ticketing-loadtest-app` |
| MariaDB | `ticketing-loadtest-mariadb` |
| Redis | `ticketing-loadtest-redis` |
| 대기열 | 제외. Redis active 사용자만 seed |
| Tomcat | `threads.max=400`, `accept-count=200` |
| Hikari | `maximum-pool-size=60` |

app, DB, Redis, k6가 모두 같은 로컬 PC에서 실행되므로 결과는 절대 성능값보다 조건 변경에 따른 상대 비교로 해석한다.

## 4. 1차 비교 실험

먼저 코드 변경 없이 경합 강도만 바꿔 60초 비교를 수행했다.

조건:

- 150 req/s
- 60초
- preAllocatedVUs 200
- maxVUs 500
- active 사용자 500명
- slow log 기준 `long_query_time=0.05`

실험군:

| 실험 | 설명 |
| --- | --- |
| 경합 없음 | 모든 요청이 서로 다른 좌석 예약 |
| 2,000석 경합 | front 100, middle 400, normal 1,500 |
| 500석 경합 | front 50, middle 150, normal 300 |
| 100석 경합 | front 20, middle 30, normal 50 |

결과:

| 실험 | iterations | dropped | avg | p95 | max | waiting p95 | 성공 | 비즈니스 실패 | 예상 밖 실패 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 경합 없음 | 9,000 | 0 | 9.32ms | 14.67ms | 179.21ms | 13.01ms | 9,000 | 0 | 0 |
| 2,000석 경합 | 9,001 | 0 | 17.58ms | 37.70ms | 472.34ms | 34.50ms | 1,400 | 7,601 | 0 |
| 500석 경합 | 9,000 | 0 | 6.49ms | 9.79ms | 93.58ms | 8.69ms | 500 | 8,500 | 0 |
| 100석 경합 | 9,000 | 0 | 6.58ms | 10.69ms | 52.86ms | 9.47ms | 100 | 8,900 | 0 |

DB 검증:

| 실험 | 예약 row | 중복 seat | TEMPORARY 좌석 |
| --- | ---: | ---: | ---: |
| 경합 없음 | 9,000 | 0 | 9,000 |
| 2,000석 경합 | 1,400 | 0 | 1,400 |
| 500석 경합 | 500 | 0 | 500 |
| 100석 경합 | 100 | 0 | 100 |

DB live sample:

| 실험 | sample 수 | 최대 `innodb_trx_count` | 최대 `innodb_lock_wait_count` | lock wait 관측 |
| --- | ---: | ---: | ---: | --- |
| 경합 없음 | 12 | 1 | 0 | 없음 |
| 2,000석 경합 | 12 | 1 | 0 | 없음 |
| 500석 경합 | 12 | 0 | 0 | 없음 |
| 100석 경합 | 12 | 1 | 0 | 없음 |

1차 판단:

- 경합 강도가 높아질수록 p95가 증가하지 않았다.
- 좌석이 빨리 소진되면 빠른 400 비즈니스 실패가 대부분을 차지해 전체 p95가 낮아졌다.
- 이 결과만으로는 row lock 병목이라고 보기 어렵다.
- 전체 p95만 보면 성공 예약 트랜잭션 비용이 가려질 수 있으므로 성공/실패 duration 분리가 필요했다.

## 5. 성공/실패 duration 분리 재측정

성공/실패 duration metric을 추가한 뒤 2,000석 경합과 500석 경합을 다시 측정했다.

조건:

- 150 req/s
- 60초
- preAllocatedVUs 200
- maxVUs 500
- active 사용자 500명
- slow log 기준 `long_query_time=0.05`

결과 파일:

- `loadtest/k6/results/contention-analysis-2000-150rps-60s-split-summary.json`
- `loadtest/k6/results/contention-analysis-500-150rps-60s-split-summary.json`

결과:

| 실험 | iterations | dropped | 전체 p95 | 예약 p95 | 성공 | 실패 | 성공 avg | 성공 p95 | 실패 avg | 실패 p95 | 예상 밖 실패 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 2,000석 경합 | 9,000 | 0 | 60.24ms | 58.57ms | 1,400 | 7,600 | 23.57ms | 65.29ms | 22.15ms | 54.10ms | 0 |
| 500석 경합 | 9,000 | 0 | 10.06ms | 10.10ms | 500 | 8,500 | 8.10ms | 11.54ms | 6.71ms | 9.95ms | 0 |

k6 세부 지표:

| 실험 | duration p95 | waiting p95 | blocked p95 | connecting p95 | receiving p95 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 2,000석 경합 | 60.24ms | 55.25ms | 5.03ms | 4.59ms | 5.26ms |
| 500석 경합 | 10.06ms | 8.97ms | 1.65ms | 1.60ms | 1.51ms |

DB sample:

| 실험 | sample 수 | 최대 `innodb_trx_count` | 최대 `innodb_lock_wait_count` | lock wait 관측 |
| --- | ---: | ---: | ---: | --- |
| 2,000석 경합 | 12 | 1 | 0 | 없음 |
| 500석 경합 | 12 | 1 | 0 | 없음 |

해석:

- 2,000석 경합에서는 성공 p95가 실패 p95보다 약간 높다.
- 하지만 성공만 압도적으로 느린 패턴은 아니다.
- 500석 경합에서는 성공과 실패 모두 10ms 안팎이다.
- DB lock wait와 slow query가 뚜렷하게 잡히지 않았다.
- 일반적인 constant-arrival-rate 경합에서는 row lock 병목이 강하게 재현되지 않았다.

## 6. 단일 좌석 burst 실험

진짜 lock 경합을 만들기 위해 단일 좌석에 300 VU가 동시에 1회씩 예약 요청을 보내는 burst 시나리오를 실행했다.

조건:

| 항목 | 값 |
| --- | --- |
| 시나리오 | `burst-reservation-contention` |
| target | `loadtest/k6/data/targets.burst-contention.json` |
| profile | `burst300` |
| executor | `per-vu-iterations` |
| VU | 300 |
| iterations | VU당 1회 |
| 대상 좌석 | seatId 10001 단일 좌석 |
| active 사용자 | 40001부터 500명 seed |
| slow log 기준 | `long_query_time=0.01` |

결과 파일:

- `loadtest/k6/results/contention-analysis-burst-1seat-300vu-summary.json`
- `loadtest/k6/results/contention-analysis-burst-1seat-300vu-db.txt`
- `loadtest/k6/results/contention-analysis-burst-1seat-300vu-slow-log.txt`
- `loadtest/k6/results/contention-analysis-burst-1seat-300vu-db-samples.txt`

k6 결과:

| 항목 | 값 |
| --- | ---: |
| iterations | 300 |
| dropped | 0 |
| 전체 HTTP p95 | 846.69ms |
| 예약 duration p95 | 920.62ms |
| 예약 성공 | 1 |
| 비즈니스 실패 | 299 |
| 예상 밖 실패 | 0 |
| 성공 avg | 56.55ms |
| 성공 p95 | 56.55ms |
| 실패 avg | 607.88ms |
| 실패 p95 | 920.65ms |

k6 세부 지표:

| 지표 | p95 |
| --- | ---: |
| `http_req_duration` | 846.69ms |
| `http_req_waiting` | 842.88ms |
| `http_req_blocked` | 66.36ms |
| `http_req_connecting` | 65.90ms |
| `http_req_receiving` | 1.60ms |
| `http_req_sending` | 2.03ms |

DB 검증:

| 항목 | 값 |
| --- | ---: |
| 예약 row | 1 |
| 중복 seat | 0 |
| `TEMPORARY` 좌석 | 1 |
| 대상 seatId | 10001 |

DB sample:

| 항목 | 값 |
| --- | ---: |
| sample 수 | 80 |
| 최대 `innodb_trx_count` | 0 |
| 최대 `innodb_lock_wait_count` | 0 |
| lock wait 관측 sample | 0 |

Slow query:

| 항목 | 값 |
| --- | ---: |
| slow query count | 0 |

burst 해석:

- 단일 좌석 300 VU 경합에서도 중복 예약은 발생하지 않았다.
- 성공은 정확히 1건이었다.
- 실패 응답의 p95가 920ms로 크게 증가했다.
- 지연은 `http_req_waiting`에 집중되었다.
- 하지만 MariaDB slow log와 `innodb_lock_waits`에는 lock wait가 잡히지 않았다.

따라서 이번 burst 결과만으로 "DB row lock wait가 병목"이라고 확정할 수는 없다.

더 안전한 해석은 다음이다.

- 단일 좌석 burst는 애플리케이션 관점에서 대기 시간을 크게 만들었다.
- 다만 그 대기가 DB slow query로 남지는 않았다.
- 외부 DB sampling은 1초 안팎으로 끝난 burst의 순간 대기를 놓쳤을 수 있다.
- 또는 Tomcat 요청 처리, Hikari connection acquire, Redis active token 확인, 애플리케이션 내부 lock 획득 전 대기 등 DB query 이전 구간에서 지연이 생겼을 수 있다.

## 7. 단일 좌석 burst 동시 관측 재실행

2026-06-11에는 같은 단일 좌석 burst를 다시 실행하면서 k6 외부 지표, loadtest AOP timer, Actuator/Hikari/Tomcat, DB sample, Docker stats를 동시에 수집했다.

목적은 `http_req_waiting` 증가가 다음 중 어디에서 발생하는지 분리하는 것이었다.

- Tomcat thread 포화
- Hikari connection pool 대기
- DB row lock wait
- Redis active token 검증
- Redis 분산락 대기
- 애플리케이션 내부 비즈니스 로직

이번 재실행에서는 운영 코드를 변경하지 않았다. 기존 loadtest 전용 AOP timer와 Actuator endpoint만 사용했다.

조건:

| 항목 | 값 |
| --- | --- |
| 실행일 | 2026-06-11 |
| 시나리오 | `burst-reservation-contention` |
| target | `loadtest/k6/data/targets.burst-contention.json` |
| profile | `burst300` |
| executor | `per-vu-iterations` |
| VU | 300 |
| iterations | VU당 1회 |
| 대상 좌석 | seatId 10001 단일 좌석 |
| active 사용자 | 40001부터 500명 seed |
| slow log 기준 | `long_query_time=0.01` |
| Tomcat | `threads.max=400`, `accept-count=200` |
| Hikari | `maximum-pool-size=60`, `minimum-idle=20` |

결과 파일:

- `loadtest/k6/results/contention-analysis-burst-1seat-300vu-observed-20260611-110232-summary.json`
- `loadtest/k6/results/contention-analysis-burst-1seat-300vu-observed-20260611-110232-summary.txt`
- `loadtest/k6/results/contention-analysis-burst-1seat-300vu-observed-20260611-110232-loadtest-timers-final.json`
- `loadtest/k6/results/contention-analysis-burst-1seat-300vu-observed-20260611-110232-actuator-metrics.csv`
- `loadtest/k6/results/contention-analysis-burst-1seat-300vu-observed-20260611-110232-docker-stats.jsonl`
- `loadtest/k6/results/contention-analysis-burst-1seat-300vu-observed-20260611-110232-db-samples.tsv`
- `loadtest/k6/results/contention-analysis-burst-1seat-300vu-observed-20260611-110232-db.txt`
- `loadtest/k6/results/contention-analysis-burst-1seat-300vu-observed-20260611-110232-slow-log.txt`

### 7.1 k6 결과

| 항목 | 값 |
| --- | ---: |
| iterations | 300 |
| dropped | 0 |
| http requests | 900 |
| `http_req_duration` avg | 895.43ms |
| `http_req_duration` p95 | 4233.37ms |
| `http_req_waiting` avg | 889.78ms |
| `http_req_waiting` p95 | 4230.28ms |
| `http_req_blocked` p95 | 31.35ms |
| `http_req_connecting` p95 | 24.07ms |
| `http_req_sending` p95 | 3.52ms |
| `http_req_receiving` p95 | 7.73ms |
| 예약 성공 | 1 |
| 비즈니스 실패 | 299 |
| 예상 밖 실패 | 0 |
| 예약 전체 p95 | 4508.04ms |
| 예약 성공 p95 | 498.07ms |
| 예약 비즈니스 실패 p95 | 4508.56ms |

`http_req_failed`는 0.3322로 집계되었다. 이는 400 비즈니스 실패 응답이 k6 HTTP 기본 기준에서는 failed로 잡히기 때문이다. 시나리오 check는 201과 400을 모두 기대 응답으로 처리했고, `reservation_unexpected_failure_rate`는 0이었다.

### 7.2 loadtest AOP timer

| 구간 | outcome | count | mean | p95 | max |
| --- | --- | ---: | ---: | ---: | ---: |
| `ticketing.loadtest.queue.active.validation` | success | 300 | 20.79ms | 83.85ms | 170.25ms |
| `ticketing.loadtest.reservation.facade` | success | 1 | 166.87ms | 159.38ms | 166.87ms |
| `ticketing.loadtest.reservation.facade` | error | 299 | 2169.52ms | 3892.05ms | 4578.88ms |
| `ticketing.loadtest.reservation.lock` | success | 1 | 153.36ms | 150.99ms | 153.36ms |
| `ticketing.loadtest.reservation.lock` | error | 299 | 2168.94ms | 3892.05ms | 4578.69ms |
| `ticketing.loadtest.reservation.service` | success | 1 | 36.85ms | 35.65ms | 36.85ms |
| `ticketing.loadtest.reservation.service` | error | 299 | 4.34ms | 10.94ms | 191.90ms |
| `ticketing.loadtest.db.seat.find` | success | 300 | 2.54ms | 8.09ms | 24.18ms |
| `ticketing.loadtest.db.seat.update` | success | 1 | 12.93ms | 12.58ms | 12.93ms |
| `ticketing.loadtest.db.reservation.insert` | success | 1 | 7.94ms | 7.86ms | 7.94ms |

핵심은 실패 299건의 `reservation.lock` p95가 3892.05ms인 반면, 실제 `reservation.service` 실패 p95는 10.94ms라는 점이다.

즉 실패 요청은 비즈니스 로직 안에서는 빠르게 "이미 예약된 좌석"으로 판단된다. 하지만 그 판단 지점에 도달하기 전, 같은 `seat:10001` Redisson lock을 얻기 위해 줄을 서면서 대기 시간이 커졌다.

실험 당시 예약 흐름은 `DistributedLockReservationFacade`가 `seat:{seatId}` 단위 lock을 잡고, lock 안에서 `DefaultReservationService.reserve()`를 실행했다. 단일 좌석으로 300명이 동시에 들어오면 성공 1건 이후의 실패 요청도 lock을 획득한 뒤에야 좌석 상태를 읽고 실패했다. 이 구조에서는 중복 예약을 막는 대신, 동일 좌석 실패 응답도 lock 대기열을 거치게 된다.

### 7.3 Actuator/Hikari/Tomcat 결과

| 지표 | samples | min | avg | max |
| --- | ---: | ---: | ---: | ---: |
| `tomcat.threads.busy` | 30 | 1 | 18.53 | 211 |
| `tomcat.threads.current` | 30 | 50 | 85.17 | 261 |
| `tomcat.threads.config.max` | 30 | 400 | 400.00 | 400 |
| `hikaricp.connections.active` | 30 | 0 | 0.17 | 1 |
| `hikaricp.connections.idle` | 30 | 19 | 19.83 | 20 |
| `hikaricp.connections.pending` | 30 | 0 | 0.00 | 0 |
| `hikaricp.connections.max` | 30 | 60 | 60.00 | 60 |
| `hikaricp.connections.min` | 30 | 20 | 20.00 | 20 |

Tomcat busy thread는 순간적으로 211까지 올라갔지만 max 400에는 도달하지 않았다. Hikari pending은 전 구간 0이었다. Hikari active도 최대 1이었다.

따라서 이번 burst에서 `http_req_waiting` p95가 4초 이상으로 커진 원인을 Tomcat thread 포화나 Hikari connection pool 대기라고 보기는 어렵다.

### 7.4 DB/Docker 관측

DB sample:

| 지표 | samples | min | avg | max |
| --- | ---: | ---: | ---: | ---: |
| `Threads_connected` | 23 | 21 | 21.00 | 21 |
| `Threads_running` | 23 | 1 | 1.04 | 2 |
| `Max_used_connections` | 23 | 21 | 21.00 | 21 |
| `Innodb_row_lock_current_waits` | 23 | 0 | 0.00 | 0 |
| `Innodb_row_lock_waits` | 23 | 0 | 0.00 | 0 |
| `Innodb_row_lock_time` | 23 | 0 | 0.00 | 0 |
| `innodb_trx_count` | 23 | 0 | 0.04 | 1 |
| `innodb_lock_wait_count` | 23 | 0 | 0.00 | 0 |

DB 검증:

| 항목 | 값 |
| --- | ---: |
| 예약 row | 1 |
| 중복 seat | 0 |
| `TEMPORARY` 좌석 | 1 |
| 대상 seatId | 10001 |
| 최종 `Max_used_connections` | 21 |
| 최종 `Threads_running` | 1 |
| 최종 `Innodb_row_lock_waits` | 0 |

Docker stats:

| container | samples | CPU avg | CPU max |
| --- | ---: | ---: | ---: |
| `ticketing-loadtest-app` | 6 | 139.66% | 515.36% |
| `ticketing-loadtest-mariadb` | 6 | 3.48% | 7.73% |
| `ticketing-loadtest-redis` | 6 | 4.52% | 19.62% |

slow query는 1건 잡혔지만, 이는 테스트 이후 검증용으로 실행한 `SELECT COUNT(*) AS temporary_seat_count FROM seats WHERE schedule_id = 1 AND status = 'TEMPORARY'`였다. 예약 API 경로의 slow query로 보기는 어렵다.

### 7.5 재실행 해석

이번 관측으로 `http_req_waiting`의 위치가 상당히 좁혀졌다.

1. Tomcat thread 포화는 아니다.

`tomcat.threads.busy` max가 211이고 설정 상한은 400이었다. 요청을 받을 worker thread 자체가 꽉 차서 대기했다고 보기는 어렵다.

2. Hikari connection pool 대기도 아니다.

`hikaricp.connections.pending`은 0이고 active connection도 최대 1이었다. DB connection을 기다리느라 밀린 흐름은 아니다.

3. MariaDB row lock wait도 아니다.

`Innodb_row_lock_waits`, `Innodb_row_lock_current_waits`, `innodb_lock_wait_count`가 모두 0이었다. slow query도 예약 API 경로에서는 잡히지 않았다.

4. 실제 지연은 Redisson lock executor 구간에 집중된다.

`reservation.lock` 실패 p95가 3892.05ms이고, `reservation.service` 실패 p95는 10.94ms였다. 이 차이는 lock 획득 전 대기 시간이 대부분임을 의미한다.

5. 이것은 기능 오류라기보다 경합 처리 정책의 결과다.

단일 좌석에 300명을 동시에 active로 들여보내면, 성공 1건 이후의 299명도 동일 lock을 차례로 획득한 뒤 실패를 확인한다. 그래서 중복 예약은 막히지만 실패 응답도 느려진다.

### 7.6 관측 부하에 대한 주의

위 실행은 AOP timer, Actuator, Docker stats, DB sample을 동시에 붙인 diagnostic run이다. 따라서 이 실행의 p95를 그대로 안정 기준선으로 사용하면 안 된다.

같은 조건에서 collector 없이 control run을 다시 실행했다.

결과 파일:

- `loadtest/k6/results/contention-analysis-burst-1seat-300vu-control-nocollector-20260611-110848-summary.json`
- `loadtest/k6/results/contention-analysis-burst-1seat-300vu-control-nocollector-20260611-110848-summary.txt`

control run 결과:

| 항목 | 값 |
| --- | ---: |
| iterations | 300 |
| 예약 성공 | 1 |
| 비즈니스 실패 | 252 |
| 예상 밖 실패 | 47 |
| `http_req_duration` p95 | 1056.20ms |
| `http_req_waiting` p95 | 1043.91ms |
| 예약 전체 p95 | 1209.98ms |
| 예약 성공 p95 | 32.56ms |
| 예약 비즈니스 실패 p95 | 1214.51ms |

collector 없는 실행에서는 p95가 다시 1초대까지 내려갔다. 하지만 대신 47건의 `connection refused`가 발생했다.

이 결과는 다음을 의미한다.

- collector 포함 실행은 느렸지만 300건이 모두 애플리케이션에 진입했다.
- collector 없는 control run은 더 날카로운 burst가 만들어졌고, 일부 요청은 애플리케이션에 진입하지 못했다.
- 진입하지 못한 요청은 Redisson lock 대기열에 서지 않으므로, lock 대기 p95를 낮게 보이게 만들 수 있다.
- 따라서 두 실행의 p95 차이를 "collector만의 오버헤드"라고 단정할 수 없다.

정확한 표현은 다음이 맞다.

- collector는 로컬 PC에서 CPU, HTTP endpoint, Docker daemon, DB sampling 비용을 추가하므로 짧은 burst 결과를 흔들 수 있다.
- 그러나 이번 차이는 관측 오버헤드만이 아니라, 300개 요청 중 몇 개가 실제로 애플리케이션 내부 lock 대기열까지 들어왔는지의 차이도 포함한다.
- 단일 좌석 burst는 매우 짧고 날카로운 테스트라서, 로컬 Docker 환경에서는 결과 변동성이 크다.

따라서 이번 diagnostic run은 `http_req_waiting`의 위치를 찾는 용도로 사용하고, 안정적인 성능 기준선은 collector를 최소화한 상태에서 반복 실행으로 별도 측정해야 한다.

## 8. 종합 결론

현재까지의 결론은 다음이다.

1. 중복 예약 방지는 정상이다.

모든 실험에서 중복 seat는 0건이었다. 단일 좌석 burst에서도 예약 row는 1건만 생성되었다.

2. 일반 인기 좌석군 경합은 row lock 병목을 강하게 만들지 않는다.

150 req/s constant-arrival-rate에서는 좌석이 이미 선점된 뒤 빠르게 400으로 반환되는 요청이 많다. 이 때문에 경합 강도를 높여도 전체 p95가 증가하지 않았다.

3. 성공/실패 duration 분리는 필요했다.

이전에는 전체 p95만 보았기 때문에 빠른 실패 응답이 성공 트랜잭션 비용을 가릴 수 있었다. 새 metric으로 성공과 실패를 분리해 보니, 일반 경합에서는 성공이 약간 더 느리지만 압도적인 차이는 아니었다.

4. 단일 좌석 burst의 지연은 DB row lock보다 Redis 분산락 대기 쪽으로 좁혀졌다.

초기 burst에서는 실패 p95가 920ms까지 증가했지만, DB slow log와 `innodb_lock_waits`에는 잡히지 않았다. 이후 AOP timer와 Actuator/Hikari/Tomcat을 함께 본 재실행에서는 실패 요청의 `reservation.lock` p95가 3892.05ms, `reservation.service` 실패 p95가 10.94ms로 분리되었다.

따라서 collector 포함 diagnostic run에서 애플리케이션에 진입한 요청의 지연은 Tomcat, Hikari, MariaDB row lock이 아니라 동일 좌석 Redisson lock을 기다리는 구간이 주된 원인으로 보는 것이 가장 타당하다.

다만 p95의 절대값은 관측 부하와 burst 진입률에 영향을 받았다. collector 없는 control run에서는 p95가 낮아졌지만 47건의 connection refused가 발생했으므로, 해당 결과 역시 안정 기준선으로 쓰기 어렵다.

5. 실험 당시 구조는 정합성에는 강하지만 동일 좌석 실패 응답이 lock 대기열을 탔다.

`seat:{seatId}` 단위 lock 안에서 좌석 상태를 확인했기 때문에 중복 예약은 막혔다. 대신 이미 예약된 좌석에 대한 실패 요청도 lock을 획득할 때까지 기다렸다. 이 지연은 코드 오류라기보다 당시 동시성 제어 전략의 비용이었다.

## 9. 후속 진행 방향과 반영 결과

이 실험에서 제안한 방향 중 빠른 실패와 admission 비교는 후속 작업으로 완료했습니다.

| 제안 | 반영 상태 |
| --- | --- |
| Redis 예약 불가 캐시로 lock 진입 전 실패 | 적용 완료 |
| lock wait time 단축 | `200ms`로 적용 완료 |
| active 사용자 수별 안정 구간 비교 | 300~2000명 조건으로 완료 |
| DB 조건부 갱신을 통한 최종 정합성 방어 | 이후 [DefaultReservationService](../../src/main/java/com/ticketing/application/reservation/service/DefaultReservationService.java)에 적용 |
| waiting queue와 예약 부하의 end-to-end 검증 | 미완료 |

병목 위치 탐색과 안정 기준선 측정은 앞으로도 다음처럼 분리해 해석합니다.

### 9.1 diagnostic run과 baseline run 분리

앞으로 같은 단일 좌석 burst를 두 종류로 나누어 실행한다.

| 실행 유형 | 목적 | collector | 판정 기준 |
| --- | --- | --- | --- |
| diagnostic run | 병목 위치 확인 | AOP, Actuator, DB sample 사용 | 어느 내부 구간이 느린지 |
| baseline run | 성능 기준선 확인 | collector 최소화 | p95, 실패율, connection refused 여부 |

diagnostic run은 관측 오버헤드를 감수하고 병목 위치를 찾는 실행이다. baseline run은 관측을 최소화하고 같은 조건을 3회 이상 반복해 중앙값 또는 가장 안정적인 값을 기준으로 삼는다.

### 9.2 active admission 기준선 측정

단일 좌석 300명 동시 진입은 collector 포함 실행에서는 실패 p95를 4초 이상으로 만들었고, collector 없는 실행에서는 일부 요청이 connection refused로 튕겼다. 따라서 active 사용자를 한 번에 얼마나 열어줄지 결정하는 기준선이 필요했다.

후속 실험:

- 단일 좌석 burst VU 50 / 100 / 200 / 300 비교
- 인기 좌석군 burst 100석 / 500석 / 2,000석 비교
- 각 조건에서 성공 1건 또는 제한된 성공 이후 실패 p95가 어느 수준까지 올라가는지 확인
- connection refused 또는 unexpected failure가 발생하는 지점 확인
- 현재 PC 기준 권장 active batch size 도출

후속 [Active admission 실험](./active-admission-loadtest-result.md)에서는 150 iterations/s, 인기 좌석 500석 조건에서 active 1000명을 보수적 기준으로 유지했고, 1500명과 2000명도 관측 범위에서는 안정적으로 통과했습니다.

### 9.3 실패 응답 fast-fail 전략 검토

실험 당시 구조는 실패 요청도 lock을 기다렸다. 당시 검토한 전략은 다음과 같다.

- lock 밖에서 좌석 상태를 먼저 조회해 이미 예약된 좌석은 빠르게 거절
- 좌석 sold-out 상태를 Redis cache에 반영하고, lock 진입 전 fast-fail
- lock wait time을 줄여 일정 시간 안에 lock을 못 얻으면 빠르게 실패

이후 DB 상태를 lock 밖에서 먼저 읽는 방식 대신 Redis 예약 불가 캐시를 사용해 이미 점유된 좌석을 빠르게 거절하고, lock wait 상한을 `200ms`로 줄였습니다. 효과와 정합성 결과는 [빠른 실패 결과](./reservation-fast-fail-loadtest-result.md)에 기록했습니다.

### 9.4 포트폴리오 정리 포인트

이번 단계에서 설명 가능한 메시지는 다음이다.

- 단일 좌석 경합에서 중복 예약은 발생하지 않았다.
- diagnostic run 기준, 애플리케이션에 진입한 요청의 지연은 DB row lock이나 Hikari 대기가 아니라 Redis 분산락 대기 구간에서 발생했다.
- 실험 당시에는 실패 요청도 lock 안에서 좌석 상태를 확인했기 때문에, 경합이 큰 좌석일수록 실패 응답이 느려졌다.
- 다만 짧은 burst의 절대 p95는 관측 부하와 connection refused 여부에 흔들리므로, 기준선 측정은 collector 최소화 후 반복 실행으로 별도 수행해야 한다.
- 따라서 해결 방향은 Tomcat/Hikari 증설이 아니라 active admission 조절, accept backlog 검토, fast-fail 정책 검토다.
