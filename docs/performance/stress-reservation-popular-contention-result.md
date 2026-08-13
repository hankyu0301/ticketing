# 인기 좌석군 경합 부하테스트 결과

> 이 문서는 2026-06-09에 수행한 초기 2,000석 경합 기준선입니다. 이후 [병목 탐색](./reservation-contention-bottleneck-analysis.md), [빠른 실패 및 lock wait 튜닝](./reservation-fast-fail-loadtest-result.md), [active admission 검증](./active-admission-loadtest-result.md) 순서로 실험을 확장했습니다. 최신 실행 절차는 [loadtest 가이드](../../loadtest/README.md)를 기준으로 합니다.

## 1. 실행 목적

이번 테스트의 목적은 실제 티켓팅 상황처럼 좌석을 인기순 구역으로 나누고, active 상태의 사용자들이 동일한 인기 좌석군에 몰릴 때 중복 예약 방지가 정상 동작하는지 확인하는 것이다.

기존의 경합 없는 예약 부하테스트는 "대기열 없이 active 사용자들이 서로 다른 좌석을 예약할 때 어느 정도 요청을 처리할 수 있는가"를 보는 기준선이었다. 이번 테스트는 그 다음 단계로, 여러 사용자가 제한된 인기 좌석군을 두고 경쟁할 때 다음 조건이 만족되는지 확인한다.

- 예약 성공은 실제 좌석 수를 초과하지 않아야 한다.
- 같은 좌석에 예약 row가 2개 이상 생기면 안 된다.
- 이미 선점된 좌석에 대한 요청은 서버 장애가 아니라 비즈니스 실패로 분리되어야 한다.
- slow query와 k6 지표를 통해 다음 병목 후보를 추정할 수 있어야 한다.

## 2. 실행 환경

| 항목 | 값 |
| --- | --- |
| 실행일 | 2026-06-09 |
| 실행 PC | 로컬 Windows 개발 PC |
| 대상 profile | `loadtest` |
| Spring Boot | `docker-compose.loadtest.yml`의 `ticketing-loadtest-app` |
| DB | `ticketing-loadtest-mariadb`, MariaDB 10.6 |
| Redis | `ticketing-loadtest-redis`, Redis 7 |
| Tomcat 설정 | `threads.max=400`, `threads.min-spare=50`, `accept-count=200` |
| Hikari 설정 | `maximum-pool-size=60`, `minimum-idle=20` |
| 인증/대기열 처리 | loadtest 전용 세션 발급 + Redis active seed |

이번 실험은 대기열 admission 자체를 측정하지 않는다. Redis에 active 사용자를 미리 주입하고, 예약 요청 경합만 측정한다.

## 3. 시나리오

사용 시나리오는 `stress-reservation-contention`이다.

### Smoke

| 항목 | 값 |
| --- | --- |
| targets 파일 | `loadtest/k6/data/targets.smoke.json` |
| 실행 시간 | 20초 |
| 목표 요청 | 10 iterations/s |
| active 사용자 | 50명 |
| 최대 VU | 40 |
| 경합 좌석 | 35석 |

Smoke 좌석군은 다음과 같다.

| 구역 | seatId 범위 | 좌석 수 | 선택 가중치 |
| --- | --- | ---: | ---: |
| front | 101-105 | 5 | 70 |
| middle | 106-115 | 10 | 20 |
| normal | 116-135 | 20 | 10 |

### Baseline

| 항목 | 값 |
| --- | --- |
| targets 파일 | `loadtest/k6/data/targets.large.json` |
| 실행 시간 | 120초 |
| 목표 요청 | 150 iterations/s |
| active 사용자 | 500명 |
| VU 설정 | preAllocatedVUs 200, maxVUs 500 |
| 경합 좌석 | 2,000석 |

Baseline 좌석군은 다음과 같다.

| 구역 | seatId 범위 | 좌석 수 | 선택 가중치 |
| --- | --- | ---: | ---: |
| front | 10001-10100 | 100 | 60 |
| middle | 10101-10500 | 400 | 30 |
| normal | 10501-12000 | 1,500 | 10 |

## 4. 실행 명령

Smoke 실행 전 초기화:

```bash
bash ./gradlew loadtestRunReset --no-daemon --console=plain
bash ./gradlew loadtestResetHotSchedule --no-daemon --console=plain
bash ./gradlew loadtestResetRedisQueue --no-daemon --console=plain
bash ./gradlew loadtestSeedRedisQueue \
  -PactiveStartMemberId=20101 -PactiveCount=50 -PwaitingCount=0 \
  --no-daemon --console=plain
bash ./gradlew loadtestResetSlowLog -PlongQueryTime=0.05 --no-daemon --console=plain
```

Smoke 실행:

```bash
bash ./gradlew loadtestRunK6 \
  -Pscenario=stress-reservation-contention \
  -Ptargets=loadtest/k6/data/targets.smoke.json \
  -Pprofile=smoke \
  -Presult=loadtest/k6/results/stress-contention-popular-smoke \
  --no-daemon --console=plain
```

Baseline 실행 전 초기화:

```bash
bash ./gradlew loadtestResetHotSchedule --no-daemon --console=plain
bash ./gradlew loadtestResetRedisQueue --no-daemon --console=plain
bash ./gradlew loadtestSeedRedisQueue \
  -PactiveStartMemberId=30001 -PactiveCount=500 -PwaitingCount=0 \
  --no-daemon --console=plain
bash ./gradlew loadtestResetSlowLog -PlongQueryTime=0.05 --no-daemon --console=plain
```

Baseline 실행:

```bash
bash ./gradlew loadtestRunK6 \
  -Pscenario=stress-reservation-contention \
  -Ptargets=loadtest/k6/data/targets.large.json \
  -Pprofile=baseline \
  -Presult=loadtest/k6/results/stress-contention-popular-baseline \
  --no-daemon --console=plain
```

## 5. Smoke 결과

| 지표 | 값 |
| --- | ---: |
| 완료 iteration | 200 |
| dropped iteration | 0 |
| 전체 HTTP 요청 | 240 |
| 평균 HTTP duration | 24.57ms |
| p95 HTTP duration | 70.40ms |
| 예약 성공 | 35건 |
| 예약 비즈니스 실패 | 165건 |
| 예약 예상 밖 실패 | 0건 |

Smoke는 정상 통과했다. 경합 좌석이 35석이므로 예약 성공 35건, 이미 선점된 좌석에 대한 비즈니스 실패 165건이 발생했다.

## 6. Baseline 결과

Baseline은 120초 동안 active 사용자 500명 범위에서 예약 요청 17,846건을 처리했다.

| 지표 | 값 |
| --- | ---: |
| 완료 iteration | 17,846 |
| dropped iteration | 155 |
| 전체 HTTP 요청 | 18,346 |
| 평균 HTTP duration | 88.56ms |
| p95 HTTP duration | 562.29ms |
| max HTTP duration | 2,017.90ms |
| 예약 성공 | 2,000건 |
| 예약 비즈니스 실패 | 15,846건 |
| 예약 예상 밖 실패 | 0건 |

`http_req_failed`는 86.37%로 보이지만, 이 값은 대부분 예약 API의 400 응답 때문이다. 이번 시나리오에서 400은 "이미 선점된 좌석"에 대한 정상적인 비즈니스 실패로 분류한다. 별도로 기록한 `reservation_unexpected_failure_rate`는 0%였다.

## 7. DB 검증 결과

Baseline 실행 후 DB 상태는 다음과 같다.

| 검증 항목 | 결과 |
| --- | ---: |
| `reservations` 총 건수 | 2,000 |
| `reservations.status=PENDING` | 2,000 |
| 중복 예약된 seat 수 | 0 |
| `seats.status=TEMPORARY` | 2,000 |
| `seats.status=AVAILABLE` | 48,000 |

좌석군별 선점 결과:

| 구역 | 전체 좌석 수 | TEMPORARY 좌석 수 |
| --- | ---: | ---: |
| front | 100 | 100 |
| middle | 400 | 400 |
| normal | 1,500 | 1,500 |

결론적으로 인기 좌석군 2,000석이 정확히 한 번씩만 예약되었고, 같은 좌석에 예약 row가 중복 생성되지 않았다.

## 8. Slow Query 결과

`long_query_time=0.05` 기준으로 Baseline 실행 중 slow log는 56건 기록되었다.

상위 slow log는 다음 패턴이었다.

| 순위 | query_time | lock_time | SQL |
| ---: | ---: | ---: | --- |
| 1 | 0.256s | 0.000s | `COMMIT` |
| 2 | 0.252s | 0.000s | `COMMIT` |
| 3 | 0.187s | 0.000s | `COMMIT` |
| 4 | 0.135s | 0.005s | `INSERT INTO reservations ...` |
| 5 | 0.134s | 0.000s | `COMMIT` |

현재 상위 slow log만 보면 특정 조회 쿼리의 full scan 문제가 먼저 보이기보다는, 실제 예약 성공 구간에서 트랜잭션 커밋과 INSERT가 순간적으로 밀리는 모습에 가깝다.

## 9. 지연 해석

k6 세부 지표는 다음과 같다.

| 지표 | avg | p95 | max |
| --- | ---: | ---: | ---: |
| `http_req_duration` | 88.56ms | 562.29ms | 2,017.90ms |
| `http_req_waiting` | 82.27ms | 518.75ms | 1,961.38ms |
| `http_req_blocked` | 3.61ms | 10.19ms | 370.95ms |
| `http_req_connecting` | 3.30ms | 9.13ms | 370.95ms |
| `http_req_sending` | 0.29ms | 0.71ms | 269.90ms |
| `http_req_receiving` | 6.00ms | 25.33ms | 470.00ms |

p95를 가장 크게 끌어올린 값은 `http_req_waiting`이다. 즉 k6 기준으로는 서버 응답 첫 바이트를 기다리는 시간이 길었다.

성공 응답만 따로 보면 `http_req_duration{expected_response:true}`의 p95는 약 1,275.77ms였다. 이는 실제 좌석을 획득한 요청이 Redis 분산락, 좌석 조회·상태 변경, 예약 INSERT, COMMIT을 모두 수행하기 때문이다. 반대로 좌석이 이미 선점된 뒤의 실패 요청은 더 빠르게 반환될 수 있다.

Actuator의 `/api/v1/reservations` 누적 요청 수는 smoke와 baseline을 합쳐 18,046건이었다.

| 상태 | count |
| --- | ---: |
| 201 | 2,035 |
| 400 | 16,011 |

이 값은 smoke의 예약 성공 35건과 baseline의 예약 성공 2,000건을 합친 값과 일치한다.

## 10. 판단

이번 테스트는 "인기 좌석군 경합 상황에서 중복 예약 방지가 정상 동작하는가"에 대해서는 의미 있는 기준선이다.

확인된 점:

- active 사용자 500명 범위에서 120초 동안 예약 요청 17,846건을 처리했다.
- 인기 좌석군 2,000석은 정확히 2,000건만 예약되었다.
- 중복 예약된 좌석은 0건이다.
- 예상 밖 실패는 0건이다.
- 부하 중 일부 요청은 목표 속도를 따라가지 못해 dropped iteration 155건이 발생했다.
- 지연 상위 구간은 단순 조회 쿼리보다는 예약 성공 트랜잭션의 커밋/쓰기 구간으로 추정된다.

주의할 점:

- 이번 테스트는 대기열 admission 비용을 포함하지 않는다.
- active 사용자 500명은 이번 PC와 현재 설정에서의 단일 기준일 뿐, 운영 기준값으로 바로 사용할 수 없다.
- k6의 `http_req_failed`는 400 비즈니스 실패까지 실패로 집계하므로, 장애율로 해석하면 안 된다.
- Actuator 지표는 smoke와 baseline 이후의 누적값이므로, k6 summary JSON을 1차 근거로 사용해야 한다.

## 11. 후속 검증 상태

이 기준선에서 확인한 다음 과제는 이후 문서에서 다음과 같이 진행했습니다.

| 과제 | 상태 | 결과 문서 |
| --- | --- | --- |
| 성공·실패 duration 분리와 단일 좌석 burst 병목 탐색 | 완료 | [예약 좌석 경합 병목 탐색](./reservation-contention-bottleneck-analysis.md) |
| 예약 불가 캐시와 lock wait 기반 빠른 실패 | 완료 | [빠른 실패 및 lock wait 튜닝](./reservation-fast-fail-loadtest-result.md) |
| active 300~2000명 admission 비교 | 완료 | [Active admission 기준](./active-admission-loadtest-result.md) |
| waiting queue와 예약 부하를 결합한 end-to-end admission 검증 | 미완료 | 후속 과제 |

따라서 이 문서의 2,000석 결과는 초기 정합성 기준선으로 사용하고, 현재 포트폴리오의 대표 경합 성능 수치는 빠른 실패 적용 후의 500석 결과를 사용합니다.
