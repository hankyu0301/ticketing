# 예약 시스템 부하테스트 및 성능 개선 요약

> 이 문서는 경합 없는 고유 좌석 예약의 초기 처리량 기준선입니다. 이후 실험은 [인기 좌석군 경합](./stress-reservation-popular-contention-result.md) → [병목 탐색](./reservation-contention-bottleneck-analysis.md) → [빠른 실패 튜닝](./reservation-fast-fail-loadtest-result.md) → [active admission 검증](./active-admission-loadtest-result.md) 순서로 이어집니다. 최신 실행 방법은 [loadtest 가이드](../../loadtest/README.md)를 기준으로 합니다.

## 1. 테스트 목적

이 부하테스트의 목적은 동일 좌석 경합이 없는 예약 생성 흐름에서 현재 테스트 환경이 어느 정도의 요청량을 안정적으로 처리할 수 있는지 확인하는 것이다.

또한 요청량 증가에 따라 Tomcat worker thread, Hikari connection pool, Redis, DB slow query 중 어느 지점이 먼저 병목 후보로 나타나는지 관측하고, 현재 PC와 `loadtest` profile 기준의 안정 설정값을 도출한다.

이 테스트는 단일 좌석 경합 검증이 아니라, 대기열을 통과한 active 사용자들이 서로 다른 좌석을 예약하는 상황의 기본 처리량 기준선이다.

## 2. 테스트 환경

| 항목 | 내용 |
| --- | --- |
| 애플리케이션 | Spring Boot app, Docker Compose, `loadtest` profile |
| DB | loadtest 전용 MariaDB |
| Redis | Spring Session, queue active token, Redisson lock |
| 부하 도구 | k6 |
| 실행 대상 | 예약 생성 API |

대량 seed 데이터는 다음 규모로 구성했다.

| 데이터 | 규모 |
| --- | ---: |
| 회원 | 100,000명 |
| hot schedule 좌석 | 50,000개 |
| history schedule 좌석 | 50,000개 |
| 예약 이력 | 300,000건 |
| 결제 이력 | 240,000건 |

테스트 DB는 매 실행마다 같은 초기 상태에서 시작할 수 있도록 base DB volume을 만들고, 실제 테스트 실행 시 run DB volume으로 복사해 사용한다.

## 3. 대기열/인증 처리 방식

예약 부하테스트에서는 대기열 진입 과정을 직접 수행하지 않았다. 대신 Redis active set에 사용자를 미리 넣어, 이미 대기열을 통과한 사용자 상태를 재현했다.

| 항목 | 처리 방식 |
| --- | --- |
| `/queue/enter` | 호출하지 않음 |
| waiting queue | 사용하지 않음 |
| active token 검증 | 예약 API에서 그대로 수행 |
| active token 저장 위치 | `queue:{scheduleId}:active` Redis sorted set |
| 로그인 비용 | loadtest 전용 세션 발급 API로 분리 |

active token은 별도의 문자열 토큰이 아니라 Redis sorted set의 membership이다.

```text
key: queue:{scheduleId}:active
member: memberId
score: active 만료 시각(ms)
```

예약 요청이 들어오면 `@ActiveTokenRequired` AOP가 인증된 memberId와 요청의 scheduleId를 기준으로 Redis active set을 확인한다. memberId가 active set에 있고 만료되지 않았다면 통과시키고, 만료 시각을 연장한다.

즉, 이 테스트는 "대기열을 통과한 active 사용자들이 예약 API에 진입했을 때"의 처리량과 병목을 측정한다.

## 4. 대표 시나리오

대표 시나리오는 `load-reservation-unique`이다.

| 항목 | 내용 |
| --- | --- |
| 요청 형태 | 서로 다른 좌석에 대한 예약 생성 |
| 총 예약 요청 수 | 60초 동안 30,000건 |
| active 사용자 세션 | 최대 1,000개 |
| 대상 좌석 | hot schedule의 서로 다른 좌석 |
| 동일 좌석 경합 | 없음 |
| 수행되는 검증 | active token 검증, Redisson lock, seat update, reservation insert |

이 시나리오는 동일 좌석 중복 예약 방지를 검증하는 테스트가 아니다. 경합이 없는 예약 생성 흐름에서 서버가 어느 정도의 요청량을 안정적으로 처리할 수 있는지 확인하는 기준선 테스트다.

## 5. 주요 결과

최종 구간별 계측 실행 결과는 다음과 같다.

| 항목 | 결과 |
| --- | ---: |
| 예약 요청 수 | 30,000 |
| dropped iterations | 0 |
| HTTP 실패율 | 0 |
| 예약 성공률 | 100% |
| 예약 API p95 | 86.05ms |
| DB 반영 예약 수 | 30,000 |
| 예약 후 `TEMPORARY` 좌석 | 30,000 |
| 예약 후 `AVAILABLE` 좌석 | 20,000 |

현재 테스트 환경에서는 최대 1,000개의 active 사용자 세션을 열어둔 상태에서, 60초 동안 30,000건의 경합 없는 예약 요청을 안정적으로 처리할 수 있음을 확인했다.

## 6. 구간별 병목 관측 결과

`loadtest` profile 전용 Micrometer Timer로 예약 요청 내부 구간을 나누어 측정했다.

| 구간 | p95 |
| --- | ---: |
| reservation facade | 43.91ms |
| reservation lock | 43.91ms |
| reservation service | 1.56ms |
| active token validation | 1.04ms |
| seat find | 0.58ms |
| seat update | 0.50ms |
| reservation insert | 0.44ms |

DB mapper p95와 row lock wait가 낮았기 때문에, 현재 시나리오에서는 DB 쿼리 자체가 주요 병목이라고 보기 어렵다.

가장 큰 내부 비용은 Redisson lock executor를 포함한 reservation facade 구간이었다. 다만 이는 비정상 병목이라기보다, Redis 기반 분산락을 사용하는 구조에서 발생하는 자연스러운 비용으로 해석한다.

측정 당시 구조에서 `reservation.facade`와 `reservation.lock`이 거의 같은 값을 보이는 것도 자연스럽다. `DistributedLockReservationFacade`는 lock executor를 호출하고, lock executor 안에서 예약 service를 실행하기 때문이다.

```text
ReservationFacade
-> LockExecutor
-> ReservationService
-> SeatMapper.findById
-> SeatMapper.update
-> ReservationMapper.save
```

이미 `reservation.service` p95가 낮게 측정되었으므로, lock 내부를 더 쪼개더라도 애플리케이션 코드 수준에서는 `tryLock`, `business`, `unlock` 정도까지만 볼 수 있다.

## 7. Tomcat/Hikari 설정 개선

초기 관측 실행에서는 HTTP 실패는 없었지만, 목표로 한 요청량을 모두 처리하지 못하고 dropped iteration이 발생했다. 애플리케이션/DB/Redis 지표를 함께 확인한 결과, DB query 자체보다 먼저 Tomcat worker thread와 Hikari connection pool에서 병목 후보가 나타났다.

초기 관측에서 확인한 주요 신호는 다음과 같다.

| 지표 | 관측 결과 | 해석 |
| --- | ---: | --- |
| Tomcat busy threads | 200 / 200 | 요청 처리 worker thread가 설정 상한까지 사용됨 |
| Tomcat current threads | 200 / 200 | thread pool이 최대치까지 확장됨 |
| Hikari pending | 최대 50 | DB connection을 기다리는 요청 발생 |
| Hikari idle | 0 | 즉시 사용할 수 있는 connection 부족 |
| DB row lock wait | 0 | 동일 row lock 경합이 주요 병목은 아님 |
| HTTP 실패율 | 0 | 기능 실패보다 처리 지연/처리량 부족 문제에 가까움 |

이 결과를 바탕으로 `loadtest` profile에서만 Tomcat과 Hikari 설정을 조정했다.

```yml
server:
  tomcat:
    threads:
      max: 400
      min-spare: 50
    accept-count: 200

spring:
  datasource:
    hikari:
      maximum-pool-size: 60
      minimum-idle: 20
```

각 설정의 의도는 다음과 같다.

| 설정 | 변경 이유 |
| --- | --- |
| `server.tomcat.threads.max=400` | 동시에 처리 가능한 servlet worker thread 상한을 늘려 worker thread 포화를 완화 |
| `server.tomcat.threads.min-spare=50` | 부하 시작 구간에서 thread 생성 지연을 줄이고 일정 수의 대기 thread를 미리 확보 |
| `server.tomcat.accept-count=200` | worker thread가 바쁜 순간에도 connector 대기열 여유를 확보 |
| `hikari.maximum-pool-size=60` | 동시에 DB connection을 요구하는 요청이 증가할 때 connection 대기 시간을 줄임 |
| `hikari.minimum-idle=20` | 부하 시작 직후 idle connection 부족으로 생기는 대기 시간을 줄임 |

설정 조정 후 동일한 관측 조건에서 결과가 개선되었다.

| 항목 | 개선 전 | 개선 후 |
| --- | ---: | ---: |
| 완료 iterations | 25,563 | 29,439 |
| dropped iterations | 4,438 | 561 |
| 예약 API avg | 861.22ms | 189.98ms |
| 예약 API p95 | 3907.23ms | 1271.36ms |
| Hikari pending max | 50 | 0 |
| Tomcat busy max | 200 / 200 | 314 / 400 |
| Slow queries | 389 | 49 |
| HTTP 실패율 | 0 | 0 |
| 예약 성공률 | 1.0 | 1.0 |

이후 구간별 Timer 계측을 추가하고, 테스트 상태를 다시 초기화한 뒤 최종 실행을 진행했다. 이 실행에서는 60초 동안 30,000건의 예약 요청을 dropped 없이 처리했다.

| 항목 | 최종 구간별 계측 실행 |
| --- | ---: |
| 예약 요청 수 | 30,000 |
| dropped iterations | 0 |
| 예약 API p95 | 86.05ms |
| Hikari active max | 38 / 60 |
| Hikari pending max | 3 |
| Hikari pending avg | 0.21 |
| Tomcat busy max | 177 / 400 |
| DB row lock wait | 0 |

이 결과는 단순히 thread와 pool을 크게 잡으면 된다는 의미가 아니다. 초기 실행에서 실제로 Tomcat worker thread 포화와 Hikari pending이 관측되었기 때문에, 현재 테스트 환경에서 먼저 드러난 자원 병목을 완화한 것이다.

Tomcat thread 증가는 요청 처리 대기 시간을 줄일 수 있지만, CPU context switching과 메모리 사용량을 함께 증가시킨다. Hikari pool 증가는 connection 대기를 줄일 수 있지만, DB가 감당해야 하는 동시 connection 수도 함께 증가시킨다.

따라서 thread/pool은 무조건 크게 잡는 값이 아니라, 부하테스트로 포화 지점을 확인하며 조정해야 한다. 현재 환경에서는 60초 동안 30,000건의 경합 없는 예약 요청을 처리하는 기준선으로 `Tomcat max 400`, `Hikari max 60` 설정이 유효했다.

이 설정값은 운영 환경의 최종값이 아니다. 운영 환경에서는 CPU core 수, DB connection 한계, WAS 인스턴스 수, Redis/DB 네트워크 지연, 실제 사용자 행동 패턴에 따라 다시 산정해야 한다.

## 8. Redis/DB/Slow Query 관측

최종 구간별 계측 실행에서 Redis는 30,000건의 예약 요청 동안 약 654k command를 처리했다. 요청당 약 21.8 command 수준이다.

| Redis 지표 | 결과 |
| --- | ---: |
| connected clients max | 66 |
| instantaneous ops/sec max | 11,640 |
| total commands delta | 654,293 |
| keyspace hits delta | 229,307 |
| keyspace misses delta | 56,148 |
| evicted keys delta | 0 |

DB 지표는 다음과 같다.

| DB 지표 | 결과 |
| --- | ---: |
| Threads connected max | 56 |
| Threads running max | 13 |
| Questions delta | 170,140 |
| Slow queries delta | 73 |
| InnoDB row lock waits delta | 0 |
| Created tmp disk tables delta | 0 |

slow query dump 기준 상위 항목은 대부분 `COMMIT`이었다.

| 항목 | 결과 |
| --- | --- |
| slow query count | 78 |
| 상위 query | `COMMIT` |
| 최대 query time | `0.134251s` |
| row lock wait | 0 |

`Slow queries delta=73`은 서버 상태 카운터의 실행 전후 차이이고, `slow query count=78`은 slow log 테이블 조회값입니다. 원본 기록만으로 두 수치의 수집 구간이 완전히 같았다고 보장할 수 없으므로 일대일 비교하지 않고, 쿼리 내용 분석에는 slow log dump를 사용했습니다.

이 결과는 DB statement 자체보다 transaction commit 단계에서 일부 tail latency가 발생한다는 신호다. 다만 mapper Timer의 p95가 낮고 slow query도 outlier 성격이므로, 현재 p95의 중심 원인을 DB query 실행계획 문제로 보기는 어렵다.

따라서 현재 단계에서는 모든 SQL을 전면 튜닝하기보다, 이후 경합 시나리오에서 DB row lock wait와 transaction commit tail을 다시 확인하는 것이 합리적이다.

## 9. 현재 테스트의 의미와 한계

이 테스트로 말할 수 있는 것은 다음과 같다.

- 경합 없는 예약 생성 처리량 기준선을 확보했다.
- 현재 테스트 환경에서 active 사용자 풀 1,000명, 60초 30,000건 예약 요청을 처리했다.
- 요청량 증가에 따라 Tomcat/Hikari 설정이 처리량과 latency에 미치는 영향을 확인했다.
- 현재 시나리오에서는 DB query 자체보다 thread/pool/Redis lock 비용이 먼저 관측 대상임을 확인했다.
- active token 검증, Redisson lock, DB update/insert가 포함된 실제 예약 API 경로를 기준으로 측정했다.

아직 말할 수 없는 것은 다음과 같다.

- 동일 좌석 경합 상황에서도 안정적이라는 것
- 실제 운영 환경에서도 같은 설정이 최적이라는 것
- waiting 사용자 10,000명 이상이 있는 대기열 흐름까지 검증했다는 것
- active admission 정책이 최종 확정됐다는 것
- 인기 좌석군에 요청이 몰리는 실제 티켓팅형 경합을 검증했다는 것

## 10. 후속 테스트 진행 상태

이 기준선에서 제시한 후속 과제는 다음과 같이 진행했습니다.

| 후속 과제 | 상태 | 결과 |
| --- | --- | --- |
| 인기 좌석군 경합과 중복 예약 검증 | 완료 | [인기 좌석군 경합 결과](./stress-reservation-popular-contention-result.md) |
| 성공·실패 지연 분리와 단일 좌석 burst 병목 탐색 | 완료 | [예약 좌석 경합 병목 탐색](./reservation-contention-bottleneck-analysis.md) |
| 예약 불가 캐시와 lock wait 빠른 실패 | 완료 | [빠른 실패 및 lock wait 튜닝](./reservation-fast-fail-loadtest-result.md) |
| active 300 / 500 / 1000 / 1500 / 2000 비교 | 완료 | [Active admission 기준](./active-admission-loadtest-result.md) |
| waiting queue와 예약 부하를 결합한 end-to-end 검증 | 미완료 | 후속 과제 |

이 문서의 수치는 고유 좌석 기준선으로 유지하며, 경합 상황의 대표 수치는 빠른 실패 적용 이후 문서를 사용합니다.

## 11. 요약

대기열과 Redis 기반 동시성 제어를 가진 티켓팅 예약 시스템에서, 대기열을 통과한 active 사용자들이 경합 없는 좌석을 예약하는 상황을 k6로 재현했다.

60초 동안 30,000건의 예약 요청을 처리하며 Tomcat, Hikari, Redis, DB slow query를 함께 관측했고, thread/pool 설정 조정 후 현재 테스트 환경에서 dropped 없이 처리 가능한 기준선을 확보했다.

이후 인기 좌석군 경합, 단일 좌석 병목, 빠른 실패, active admission 실험으로 확장했으며 각 결과는 위 후속 문서에 분리해 기록했습니다.
