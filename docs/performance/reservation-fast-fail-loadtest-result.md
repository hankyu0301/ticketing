# 예약 경합 빠른 실패 및 Lock Wait 튜닝 결과

> 이 문서는 [단일 좌석 경합 병목 탐색](./reservation-contention-bottleneck-analysis.md)에서 확인한 분산락 대기를 개선한 결과입니다. 후속 admission 검증은 [Active admission 기준](./active-admission-loadtest-result.md), 최신 재현 절차는 [loadtest 가이드](../../loadtest/README.md)를 참고합니다.

## 문서 목적

이 문서는 좌석 경합 부하테스트에서 내린 두 가지 의사결정을 분리해서 정리한다.

첫 번째 의사결정은 빠른 실패 전략 자체가 유효한지 확인하는 것이다.

- 락 획득 대기 시간이 초과되면 `429 Too Many Requests`로 빠르게 실패 처리
- 이미 점유된 좌석을 Redis 예약 불가 캐시에 기록하고, 락 진입 전에 `400 Bad Request`로 빠르게 실패 처리

두 번째 의사결정은 빠른 실패 전략을 적용한 뒤 `ticketing.lock.wait-time-ms`를 몇 ms로 둘지 정하는 것이다.

## 실행 환경

- 실행일: 2026-06-12
- 환경: `docker-compose.loadtest.yml`
- 컨테이너:
  - `ticketing-loadtest-app`
  - `ticketing-loadtest-mariadb`
  - `ticketing-loadtest-redis`
- Spring profile: `loadtest`
- lock strategy: `REDISSON`
- 빠른 실패 전략 검증 기준:
  - 기존 방식 기준: `ticketing.lock.wait-time-ms=10000`
  - 전략 검증 기준: `ticketing.lock.wait-time-ms=300`
- lock wait 튜닝 비교: `100ms`, `200ms`, `300ms`, `500ms`
- 최종 반영 설정:
  - `application.yml`: `ticketing.lock.wait-time-ms=200`
  - `application-loadtest.yml`: `ticketing.lock.wait-time-ms=200`

이 측정 이후 DB 조건부 갱신이 [DefaultReservationService](../../src/main/java/com/ticketing/application/reservation/service/DefaultReservationService.java)에 추가됐습니다. 따라서 이 문서의 수치는 Redis 분산락·예약 불가 캐시·lock wait 개선 효과를 나타내며, 이후 추가된 DB 최종 정합성 방어의 성능을 별도로 비교한 값은 아닙니다.

## Seed 상태

`loadtestVerifyLargeSeed` 기준 seed 상태는 다음과 같다.

| 항목 | 건수 |
| --- | ---: |
| 회원 | 100,000 |
| 공연 | 2 |
| 공연 회차 | 2 |
| 좌석 | 100,000 |
| 예약 이력 | 300,001 |
| 결제 이력 | 240,000 |

hot schedule인 `schedule_id=1`은 테스트 전 50,000석 모두 `AVAILABLE` 상태였다.

## 최종 요약

| 구분 | 결론 | 근거 |
| --- | --- | --- |
| 빠른 실패 전략 적용 | 유효함 | A 조건 대비 C 조건의 reservation p95가 `2053.08ms -> 637.11ms`로 69.0% 감소 |
| 예약 불가 캐시 | 유효함 | 이미 선점된 좌석 재요청에서 `429`가 232건에서 0건으로 감소 |
| lock wait 최종값 | `200ms` | 100/200/300/500ms 비교에서 HTTP p95와 reservation p95가 가장 낮음 |
| 인기 좌석군 검증 | 유효함 | 18,000건 처리, 예약 성공 500건, 중복 예약 0건, slow query 0건 |

이 문서에서 `300ms`는 빠른 실패 전략의 효과를 검증할 때 사용한 기준값이다. 최종 설정값은 별도 튜닝 실험 결과에 따라 `200ms`로 결정했다.

## 1. 빠른 실패 전략 적용 효과

이번 실험은 빠른 실패 전략의 효과를 한 덩어리로 보지 않고, 다음 세 조건으로 분해했다.

이 장의 목적은 "빠른 실패 전략이 성능 개선에 유효한가"를 확인하는 것이다. `300ms`가 최종값이라는 의미가 아니라, 긴 락 대기 시간과 짧은 락 대기 시간, 그리고 예약 불가 캐시를 비교하기 위한 실험 기준값이다.

| 조건 | 예약 불가 캐시 | 락 획득 대기 시간 | 목적 |
| --- | --- | ---: | --- |
| A | OFF | 10000ms | 기존 방식에 가까운 기준선 |
| B | OFF | 300ms | 락 대기 시간 단축만 적용한 효과 |
| C | ON | 300ms | 락 대기 시간 단축과 예약 불가 캐시를 함께 적용한 최종 효과 |

모든 조건은 같은 `burst-reservation-contention` 시나리오의 `burst300` profile로 실행했다. 즉, 같은 좌석 하나에 300명이 동시에 예약을 시도하는 조건이다.

계산식은 다음과 같다.

```text
지연 시간 감소율 = (이전 조건 지연 시간 - 이후 조건 지연 시간) / 이전 조건 지연 시간 * 100
처리율 증가율 = (이후 조건 iteration rate - 이전 조건 iteration rate) / 이전 조건 iteration rate * 100
```

### 조건별 결과

| 지표 | A: 캐시 OFF, 10000ms | B: 캐시 OFF, 300ms | C: 캐시 ON, 300ms |
| --- | ---: | ---: | ---: |
| 총 요청 | 300 | 300 | 300 |
| 성공 `201` | 1 | 1 | 1 |
| 비즈니스 실패 `400` | 299 | 25 | 277 |
| 락 대기 초과 `429` | 0 | 274 | 22 |
| 예상 밖 실패 | 0 | 0 | 0 |
| dropped iterations | 0 | 0 | 0 |
| iteration rate | 35.0346/s | 46.9388/s | 57.3706/s |
| HTTP p95 | 1848.92ms | 957.82ms | 562.53ms |
| reservation avg | 1309.37ms | 726.29ms | 441.62ms |
| reservation p95 | 2053.08ms | 1022.68ms | 637.11ms |

### 전략별 개선 폭

| 비교 | 의미 | 처리율 | HTTP p95 | reservation avg | reservation p95 | 429 변화 |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| A -> B | 락 대기 시간 단축 효과 | 34.0% 증가 | 48.2% 감소 | 44.5% 감소 | 50.2% 감소 | 0건 -> 274건 |
| B -> C | 예약 불가 캐시 추가 효과 | 22.2% 증가 | 41.3% 감소 | 39.2% 감소 | 37.7% 감소 | 274건 -> 22건 |
| A -> C | 최종 개선 효과 | 63.8% 증가 | 69.6% 감소 | 66.3% 감소 | 69.0% 감소 | 0건 -> 22건 |

### 해석

락 대기 시간을 `10000ms`에서 `300ms`로 줄인 것만으로도 reservation p95는 `2053.08ms -> 1022.68ms`로 50.2% 감소했다. 다만 이 경우 `429`가 274건으로 급증했다. 즉 락 대기 시간 단축은 지연을 줄이는 데 효과적이지만, 단독으로 쓰면 많은 사용자가 "잠시 후 다시 시도" 응답을 받는다.

예약 불가 캐시를 함께 적용하면 reservation p95는 `1022.68ms -> 637.11ms`로 추가 37.7% 감소했고, `429`는 274건에서 22건으로 줄었다. 이는 좌석이 점유된 뒤 반복되는 요청이 락을 오래 기다리지 않고, 예약 불가 캐시 또는 도메인 검증을 통해 빠르게 정리되기 때문이다.

최종적으로 A 조건 대비 C 조건은 reservation p95를 `2053.08ms -> 637.11ms`로 69.0% 줄였고, 처리율은 `35.0346/s -> 57.3706/s`로 63.8% 증가시켰다. 따라서 이번 개선의 핵심은 `락 대기 시간 단축만`이 아니라, `짧은 락 대기 상한 + 예약 불가 캐시`를 함께 적용해 실패 요청이 오래 머무는 시간을 줄인 것이다.

추가로 B 조건의 반복 요청 구간도 측정했다. 이 측정은 B 조건에서 좌석 1개가 이미 `TEMPORARY`로 선점된 상태를 만든 뒤, DB/Redis를 초기화하지 않고 같은 좌석에 300명이 다시 요청하는 방식으로 진행했다.

### 캐시 적중 구간 개선 폭

캐시 적중 구간은 좌석이 이미 점유되어 Redis 예약 불가 캐시가 존재하는 상태다. 실제 티켓팅에서는 특정 좌석이 선점된 뒤에도 같은 좌석에 반복 요청이 들어오는 구간에 해당한다.

| 지표 | 최초 경합 구간 | 캐시 적중 구간 | 감소 |
| --- | ---: | ---: | ---: |
| reservation avg | 441.62ms | 323.27ms | 약 26.8% |
| reservation p95 | 637.11ms | 445.44ms | 약 30.1% |
| HTTP p95 | 562.53ms | 408.66ms | 약 27.4% |

캐시 적중 구간에서는 모든 요청이 락 진입 전 Redis 예약 불가 캐시에서 걸러져 `400`으로 실패했다. 따라서 `429`는 발생하지 않았다.

### 이미 선점된 좌석 재요청에서 캐시 효과

이미 선점된 좌석에 다시 300명이 요청하는 상황을 캐시 OFF와 캐시 ON으로 비교하면 다음과 같다.

| 지표 | B: 캐시 OFF, 300ms 반복 요청 | C: 캐시 ON, 300ms 반복 요청 | 개선 폭 |
| --- | ---: | ---: | ---: |
| 총 요청 | 300 | 300 | - |
| 성공 `201` | 0 | 0 | 동일 |
| 비즈니스 실패 `400` | 68 | 300 | 232건 증가 |
| 락 대기 초과 `429` | 232 | 0 | 232건 감소 |
| 예상 밖 실패 | 0 | 0 | 동일 |
| reservation avg | 431.19ms | 323.27ms | 25.0% 감소 |
| reservation p95 | 550.19ms | 445.44ms | 19.0% 감소 |
| HTTP p95 | 522.48ms | 408.66ms | 21.8% 감소 |

이 비교가 예약 불가 캐시의 역할을 가장 직접적으로 보여준다. 캐시가 없으면 이미 선점된 좌석에 대한 반복 요청도 상당수가 락 획득 대기 시간 초과까지 흘러가 `429`로 실패한다. 캐시를 켜면 같은 요청이 락 진입 전에 예약 불가 상태로 판정되어 전부 `400`으로 정리된다.

## 2. Lock Wait Time 선정

### 목적

예약 불가 캐시를 켠 상태에서 `ticketing.lock.wait-time-ms` 값을 바꿔가며, 단일 좌석 최초 경합 구간의 적정 락 대기 시간을 찾는다.

이 장의 목적은 빠른 실패 전략이 유효하다는 전제 위에서 "그럼 현재 환경에서는 wait time을 몇 ms로 둘 것인가"를 결정하는 것이다.

이번 실험은 다음 질문에 답하기 위한 것이다.

- 너무 짧은 wait time이 `429`를 과도하게 늘리는가?
- wait time을 늘리면 `429`가 줄어드는 대신 p95 지연이 커지는가?
- 현재 PC와 loadtest 구성에서는 어떤 값이 가장 균형적인가?

### 실행 조건

- 시나리오: `burst-reservation-contention`
- profile: `burst300`
- VU: 300
- iteration: VU당 1회, 총 300회
- 좌석 범위: `seat_id=10001`, 1개 좌석
- active 사용자: 500명 seed
- 예약 불가 캐시: ON
- 비교 값: `100ms`, `200ms`, `300ms`, `500ms`
- 실행 전 작업:
  - `loadtestResetHotSchedule`
  - `loadtestResetRedisQueue`
  - `loadtestSeedRedisQueue -PactiveStartMemberId=40001 -PactiveCount=500 -PwaitingCount=0`

### 결과

| wait time | 총 요청 | 성공 `201` | 비즈니스 실패 `400` | 락 대기 초과 `429` | 예상 밖 실패 | dropped | iteration rate | HTTP p95 | reservation avg | reservation p95 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 100ms | 300 | 1 | 5 | 294 | 0 | 0 | 44.26/s | 1046.51ms | 833.74ms | 1170.51ms |
| 200ms | 300 | 1 | 234 | 65 | 0 | 0 | 46.22/s | 504.39ms | 428.44ms | 537.73ms |
| 300ms | 300 | 1 | 241 | 58 | 0 | 0 | 53.25/s | 683.29ms | 534.51ms | 735.75ms |
| 500ms | 300 | 1 | 222 | 77 | 0 | 0 | 38.50/s | 860.34ms | 656.12ms | 951.83ms |

### 해석

`100ms`는 현재 환경에서 너무 짧았다. 총 300건 중 294건이 `429`로 실패했고, reservation p95도 `1170.51ms`로 가장 높았다. 이는 락 wait time 자체를 짧게 잡아도 요청이 Tomcat thread, Spring Security/session, Redis 조회, 스케줄링 구간을 거친 뒤 락 경합 구간에 도달하기 때문에 HTTP 전체 지연이 같은 폭으로 줄지 않는다는 의미다.

`200ms`는 이번 측정에서 가장 균형적이었다. `429`는 65건으로 줄었고, HTTP p95는 `504.39ms`, reservation p95는 `537.73ms`로 네 조건 중 가장 낮았다.

`300ms`는 기존 기준값이다. `200ms`와 비교하면 `429`는 65건에서 58건으로 7건 줄었지만, reservation p95는 `537.73ms -> 735.75ms`로 약 36.8% 증가했다. 현재 PC 기준으로는 추가 대기 시간이 얻는 이득보다 p95 지연 증가가 더 컸다.

`500ms`는 더 오래 기다리지만 결과가 좋아지지 않았다. `429`도 77건으로 다시 증가했고, reservation p95는 `951.83ms`까지 커졌다. 이 구간부터는 락 대기 상한을 늘리는 방식이 경합 해소보다 요청 체류 시간 증가로 이어진다.

따라서 현재 loadtest 환경의 단일 좌석 최초 경합 기준 권장값은 `200ms`다. 이 값은 운영 절대값이 아니라 현재 PC, Docker 기반 loadtest, active 500명, burst300 조건에서의 기준선이다. 이후 active batch size를 바꾸거나 대기열 포함 시나리오를 측정하면 다시 보정해야 한다.

## 부록 A. 빠른 실패 최종 조건: 단일 좌석 최초 경합

### 목적

같은 좌석 하나에 300명이 거의 동시에 몰릴 때 락 획득 대기 시간 초과와 비즈니스 실패가 분리되는지 확인한다.

이 결과는 빠른 실패 전략 검증 당시의 `300ms` 조건 원본 측정값이다. 최종 설정값은 이후 lock wait 튜닝 결과에 따라 `200ms`로 조정했다.

### 실행 조건

- 시나리오: `burst-reservation-contention`
- targets: `loadtest/k6/data/targets.burst-contention.json`
- profile: `burst300`
- VU: 300
- iteration: VU당 1회, 총 300회
- 좌석 범위: `seat_id=10001`, 1개 좌석
- active 사용자: 500명 seed
- 실행 전 작업:
  - `loadtestResetHotSchedule`
  - `loadtestResetRedisQueue`
  - `loadtestSeedRedisQueue -PactiveStartMemberId=40001 -PactiveCount=500 -PwaitingCount=0`

### 결과

| 지표 | 값 |
| --- | ---: |
| 총 요청 | 300 |
| 성공 `201` | 1 |
| 비즈니스 실패 `400` | 277 |
| 락 대기 초과 `429` | 22 |
| 예상 밖 실패 | 0 |
| dropped iterations | 0 |
| reservation avg | 441.62ms |
| reservation p95 | 637.11ms |
| HTTP avg | 151.88ms |
| HTTP p95 | 562.53ms |

### 해석

중복 예약 방지는 정상이다. 단일 좌석에 대해 성공은 1건만 발생했고, 나머지는 `400` 또는 `429`로 실패했다.

`429`가 22건 발생한 것은 `ticketing.lock.wait-time-ms=300` 설정이 실제로 반영됐다는 근거다. 다만 최초 경합 구간에서는 모든 요청이 거의 동시에 들어오므로, Redis 예약 불가 캐시가 채워지기 전에 여러 요청이 락 진입을 시도한다. 따라서 이 구간에서는 락 대기 비용이 남는 것이 자연스럽다.

## 부록 B. 빠른 실패 최종 조건: 단일 좌석 캐시 적중 구간

### 목적

이미 Redis 예약 불가 캐시가 채워진 좌석에 대해 락 진입 전 빠른 실패가 작동하는지 확인한다.

### 실행 조건

- 실행 1 직후 DB/Redis reset 없이 동일한 burst300 실행
- 같은 좌석 `seat_id=10001`
- 총 요청 300회

### 결과

| 지표 | 값 |
| --- | ---: |
| 총 요청 | 300 |
| 성공 `201` | 0 |
| 비즈니스 실패 `400` | 300 |
| 락 대기 초과 `429` | 0 |
| 예상 밖 실패 | 0 |
| dropped iterations | 0 |
| reservation avg | 323.27ms |
| reservation p95 | 445.44ms |
| HTTP avg | 111.68ms |
| HTTP p95 | 408.66ms |

### 해석

캐시 적중 상태에서는 모든 요청이 `400`으로 분리됐고, `429`는 발생하지 않았다. 즉 락 획득 대기 시간 초과로 흐르기 전에 예약 불가 캐시 기반 빠른 실패가 동작했다.

최초 경합 구간 대비 지연은 다음 정도로 감소했다.

| 비교 지표 | 최초 경합 구간 | 캐시 적중 구간 | 감소 |
| --- | ---: | ---: | ---: |
| reservation avg | 441.62ms | 323.27ms | 약 26.8% |
| reservation p95 | 637.11ms | 445.44ms | 약 30.1% |

다만 캐시 적중 구간에서도 p95가 445ms 수준으로 남았다. 이는 락/DB 쓰기 비용은 줄었지만 HTTP 처리, Spring Security session/CSRF, Redis session 조회, Redis 캐시 조회, Tomcat scheduling 비용은 여전히 남기 때문이다.

## 3. 인기 좌석군 경합 Stress 검증

### 목적

실제 티켓팅에 가까운 인기 좌석군 경합에서 빠른 실패 적용 후 처리량, 실패 분리, 중복 예약 여부를 확인한다.

### 실행 조건

- 시나리오: `stress-reservation-contention`
- targets: `loadtest/k6/data/targets.contention-500.json`
- profile: `baseline`
- executor: `constant-arrival-rate`
- 요청률: 150 iterations/s
- 실행 시간: 2분
- 총 요청: 18,000회
- active 사용자: 2,000명 seed
- 좌석군:
  - `front`: 50석, weight 60
  - `middle`: 150석, weight 30
  - `normal`: 300석, weight 10
  - 총 500석
- 실행 전 작업:
  - `loadtestResetHotSchedule`
  - `loadtestResetRedisQueue`
  - `loadtestSeedRedisQueue -PactiveStartMemberId=30001 -PactiveCount=2000 -PwaitingCount=0`
  - `loadtestResetSlowLog`

### k6 결과

| 지표 | 값 |
| --- | ---: |
| 총 요청 | 18,000 |
| 처리율 | 149.99 iterations/s |
| 성공 `201` | 500 |
| 비즈니스 실패 `400` | 17,500 |
| 락 대기 초과 `429` | 0 |
| 예상 밖 실패 | 0 |
| dropped iterations | 0 |
| reservation avg | 7.61ms |
| reservation p95 | 13.66ms |
| 성공 요청 p95 | 28.08ms |
| 비즈니스 실패 p95 | 11.79ms |
| HTTP avg | 7.63ms |
| HTTP p95 | 13.88ms |

`http_req_failed`는 0.9511로 높게 보이지만, 이는 k6가 HTTP 400을 기본 실패로 집계하기 때문이다. 이 시나리오에서는 이미 점유된 좌석에 대한 `400`이 예상 가능한 비즈니스 실패이므로, 판단 기준은 `reservation_unexpected_failure_rate=0`이다.

### DB / Redis 결과

테스트 종료 후 DB 상태는 다음과 같다.

| 항목 | 값 |
| --- | ---: |
| schedule 1 AVAILABLE 좌석 | 49,500 |
| schedule 1 TEMPORARY 좌석 | 500 |
| schedule 1 PENDING 예약 | 500 |
| 중복 예약 좌석 수 | 0 |
| 경합 좌석군 내 점유 좌석 | 500 |
| Redis 예약 불가 캐시 set size | 500 |

MariaDB slow query log는 50ms 기준으로 0건이었다.

```text
slow_query_count = 0
```

### 해석

인기 좌석군 500석에 대해 정확히 500건만 예약 성공했고, 중복 예약은 발생하지 않았다. 이후 17,500건은 예약 불가 캐시 또는 도메인 검증을 통해 비즈니스 실패로 처리됐다.

이 결과는 빠른 실패가 장기 경합 상황에서 효과적이라는 근거가 된다. 최초 경합 구간처럼 한순간에 모든 요청이 몰리는 상황에서는 락 대기 비용이 남지만, 좌석이 점유된 이후 반복되는 요청은 빠르게 실패시켜 DB slow query 없이 150 iterations/s를 안정적으로 처리했다.

## 4. 결과 파일

k6 raw summary는 다음 파일에 저장됐다.

빠른 실패 전략 효과 분해 결과:

- `loadtest/k6/results/fastfail-decomposition-a-cache-off-wait10000-first-20260612-summary.txt`
- `loadtest/k6/results/fastfail-decomposition-b-cache-off-wait300-first-20260612-summary.txt`
- `loadtest/k6/results/fastfail-decomposition-b-cache-off-wait300-first-rerun-20260612-summary.txt`
- `loadtest/k6/results/fastfail-decomposition-b-cache-off-wait300-repeat-rerun-20260612-summary.txt`

빠른 실패 최종 적용 후 결과:

- `loadtest/k6/results/fastfail-rebuilt-burst-smoke-20260612-summary.txt`
- `loadtest/k6/results/fastfail-rebuilt-burst300-cold-20260612-summary.txt`
- `loadtest/k6/results/fastfail-rebuilt-burst300-warm-cache-20260612-summary.txt`
- `loadtest/k6/results/fastfail-rebuilt-stress-contention-baseline-20260612-summary.txt`
- 각 txt와 같은 prefix의 `summary.json`

락 획득 대기 시간 튜닝 결과:

- `loadtest/k6/results/fastfail-lockwait-cache-on-wait100-first-20260612-summary.txt`
- `loadtest/k6/results/fastfail-lockwait-cache-on-wait200-first-20260612-summary.txt`
- `loadtest/k6/results/fastfail-lockwait-cache-on-wait300-first-20260612-summary.txt`
- `loadtest/k6/results/fastfail-lockwait-cache-on-wait500-first-20260612-summary.txt`
- 각 txt와 같은 prefix의 `summary.json`

`loadtest/k6/results`는 git ignore 대상이므로, 포트폴리오 근거로는 이 문서를 대표 결과로 사용한다.

## 5. 최종 결론

이번 작업의 결론은 두 단계로 나눠야 한다.

첫째, 빠른 실패 전략은 유효했다. 기존 방식에 가까운 A 조건과 최종 빠른 실패 조건인 C 조건을 비교하면 reservation p95는 `2053.08ms -> 637.11ms`로 69.0% 감소했고, HTTP p95는 `1848.92ms -> 562.53ms`로 69.6% 감소했다. 단순히 예외 응답을 빠르게 반환한 것이 아니라, 실패 요청이 락과 DB 흐름에 오래 머무는 시간을 줄였다.

둘째, 현재 loadtest 환경의 lock wait 기준값은 `200ms`로 두는 것이 가장 균형적이다. `300ms`는 `200ms`보다 `429`를 7건 줄였지만 reservation p95가 약 36.8% 증가했다. `500ms`는 더 오래 기다리는데도 p95와 `429`가 모두 악화됐다. 따라서 현재 PC, Docker 기반 loadtest, active 500명, burst300 조건에서는 `200ms`를 기준값으로 반영한다.

셋째, 인기 좌석군 stress에서도 결과는 안정적이었다. 2분 동안 18,000건을 dropped iteration 없이 처리했고, 500석 경합에서 성공은 정확히 500건, 중복 예약은 0건이었다. MariaDB slow query도 0건이었다.

최종 반영 설정은 다음과 같다.

| 파일 | 설정 |
| --- | --- |
| `src/main/resources/application.yml` | `ticketing.lock.wait-time-ms=200` |
| `src/main/resources/application-loadtest.yml` | `ticketing.lock.wait-time-ms=200` |
