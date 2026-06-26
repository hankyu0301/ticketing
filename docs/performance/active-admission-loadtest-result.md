# Active 사용자 수별 부하테스트 및 Admission 기준 도출 결과

## 목적

이번 실험의 목적은 현재 PC와 Docker 기반 loadtest 환경에서 한 번에 몇 명의 사용자를 active 상태로 열 수 있는지 확인하는 것이다.

기존 빠른 실패 전략과 lock wait 튜닝은 좌석 경합 상황에서 실패 요청을 얼마나 빠르게 정리할 수 있는지를 확인했다. 이번 실험은 그 다음 단계로, active 사용자 수를 바꿔가며 reservation 경합 부하가 안정적으로 처리되는 구간을 찾는다.

이 문서는 기존 빠른 실패 결과 문서에 병합하지 않고, active admission 기준 도출 결과만 별도 문서로 남긴다.

## 실행 환경

- 실행일: 2026-06-12
- 환경: Docker loadtest
- app endpoint: `http://localhost:18080`
- Spring profile: `loadtest`
- DB: `ticketing-loadtest-mariadb`
- Redis: `ticketing-loadtest-redis`
- 시나리오: `stress-reservation-contention`
- target: `loadtest/k6/data/targets.contention-500.json`
- lock wait: `200ms`
- 예약 불가 캐시: ON

현재 loadtest queue 설정은 다음과 같다.

| 설정 | 값 |
| --- | ---: |
| `queue.enter-batch-size` | 500 |
| `queue.max-active-member` | 1000 |
| `queue.active-time-ms` | 1800000 |

## 실행 조건

인기 좌석군은 총 500석으로 고정했다.

| 구역 | 좌석 수 | 요청 weight |
| --- | ---: | ---: |
| front | 50 | 60 |
| middle | 150 | 30 |
| normal | 300 | 10 |
| 합계 | 500 | 100 |

요청 조건은 모든 단계에서 동일하게 유지했다.

| 항목 | 값 |
| --- | --- |
| 요청률 | 150 iterations/s |
| 실행 시간 | 2분 |
| 총 요청 목표 | 18,000건 |
| active 사용자 수 | 300, 500, 1000, 1500, 2000 |
| k6 VU 상한 | active 사용자 수와 동일 |

각 단계는 다음 순서로 실행했다.

```bash
./gradlew loadtestResetHotSchedule
./gradlew loadtestResetRedisQueue
./gradlew loadtestResetSlowLog
./gradlew loadtestSeedRedisQueue -PactiveStartMemberId=30001 -PactiveCount={ACTIVE_COUNT} -PwaitingCount=0
k6 run \
  -e BASE_URL=http://localhost:18080 \
  -e SCENARIO=stress-reservation-contention \
  -e TARGETS_FILE=loadtest/k6/data/targets.contention-500.json \
  -e LOAD_PROFILE=baseline \
  -e PRE_ALLOCATED_VUS={ACTIVE_COUNT} \
  -e MAX_VUS={ACTIVE_COUNT} \
  -e RESULT_BASENAME=loadtest/k6/results/active-admission-{ACTIVE_COUNT}-150rps-2m \
  loadtest/k6/main.js
```

## k6 결과

| active 사용자 | iterations | iteration rate | dropped | HTTP avg | HTTP p95 | reservation avg | reservation p95 | 201 | 400 | 429 | unexpected |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 300 | 17,806 | 148.37/s | 195 | 46.80ms | 61.17ms | 22.76ms | 15.65ms | 500 | 17,187 | 119 | 0 |
| 500 | 18,001 | 150.00/s | 0 | 4.48ms | 6.60ms | 4.48ms | 6.59ms | 500 | 17,501 | 0 | 0 |
| 1000 | 18,001 | 150.00/s | 0 | 4.27ms | 6.98ms | 4.37ms | 7.09ms | 500 | 17,501 | 0 | 0 |
| 1500 | 18,001 | 149.99/s | 0 | 4.15ms | 6.17ms | 4.28ms | 6.37ms | 500 | 17,501 | 0 | 0 |
| 2000 | 18,000 | 150.00/s | 0 | 3.89ms | 5.58ms | 4.03ms | 5.60ms | 500 | 17,500 | 0 | 0 |

`http_req_failed`는 판단 기준에서 제외했다. k6는 HTTP 400을 실패로 집계하지만, 이 시나리오에서 400은 이미 점유된 좌석에 대한 정상 비즈니스 실패다. 판단 기준은 `reservation_unexpected_failure_rate`, `dropped_iterations`, `429`, 중복 예약 여부다.

## DB / Redis 정합성 결과

모든 active 조건에서 예약 정합성은 정상으로 확인됐다.

| active 사용자 | TEMPORARY 좌석 | PENDING 예약 | 중복 예약 좌석 | slow query | Redis 예약 불가 캐시 | Redis active token |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 300 | 500 | 500 | 0 | 0 | 500 | 300 |
| 500 | 500 | 500 | 0 | 0 | 500 | 500 |
| 1000 | 500 | 500 | 0 | 0 | 500 | 1000 |
| 1500 | 500 | 500 | 0 | 0 | 500 | 1500 |
| 2000 | 500 | 500 | 0 | 0 | 500 | 2000 |

모든 조건에서 500석 경합에 대해 예약 성공은 정확히 500건이었고, 중복 예약은 발생하지 않았다.

## 해석

### active 300

active 300 조건은 `dropped_iterations=195`가 발생했다. 이 값만 보면 위험 구간처럼 보일 수 있지만, DB 정합성은 정상이고 unexpected failure도 0이었다.

이 조건의 핵심은 서버 정합성 실패가 아니라 k6 VU 상한 부족이다. 이번 실험에서는 active 사용자 수와 k6 `maxVUs`를 동일하게 맞췄다. active 300 조건에서는 성공 요청 일부가 길게 체류하면서 k6가 150 iterations/s를 유지하기 위한 추가 VU를 만들 수 없었고, 그 결과 dropped iteration이 발생했다.

따라서 active 300은 "서버가 300명을 못 버틴다"가 아니라, "현재 시나리오에서 active 사용자 수와 k6 VU 상한을 동일하게 묶으면 300 VU는 150 iterations/s 생성에 부족하다"로 해석해야 한다.

### active 500 이상

active 500, 1000, 1500, 2000 조건은 모두 안정적으로 통과했다.

- dropped iteration: 0
- unexpected failure: 0
- 429 lock timeout: 0
- 중복 예약: 0
- slow query: 0
- reservation p95: 5.60ms ~ 7.09ms

특히 active 1000은 현재 `queue.max-active-member=1000`과 같은 값인데, 150 iterations/s, 2분, 인기 좌석군 500석 경합 조건에서 안정 구간으로 확인됐다.

active 1500과 2000도 같은 조건에서는 안정적이었다. 다만 이번 target의 `contentionUsers.count`가 2000이므로, 코드 수정 없이 확인 가능한 상한은 2000명까지다. 즉 이번 실험에서는 active 2000까지도 병목이 드러나지 않았다.

## Admission 기준

이번 실험의 관측 범위 안에서는 active 2000명까지 안정 구간이다.

다만 admission 기준은 두 단계로 나눠 적용하는 것이 안전하다.

| 기준 | 값 | 이유 |
| --- | ---: | --- |
| 보수적 기준 | 1000명 | 현재 `queue.max-active-member`와 동일하며 안정적으로 통과 |
| 확장 가능 기준 | 1500명 | 현재 설정 초과 구간에서도 안정적 |
| 관측 상한 | 2000명 | 이번 target에서 수정 없이 확인 가능한 최대 active 수이며 안정적 |

따라서 현재 포트폴리오 문서에서 설명할 admission 기준은 다음과 같이 정리한다.

```text
현재 PC + Docker loadtest + 150 iterations/s + 인기 좌석군 500석 경합 기준으로는
active 1000명은 안정 구간으로 확인됐다.

동일 조건에서 active 1500명과 2000명도 안정적으로 통과했으므로,
현재 설정값인 queue.max-active-member=1000은 보수적인 기준이다.

다만 active 2000명은 이번 관측 범위의 상한일 뿐이며,
더 높은 요청률 또는 대기열 포함 시나리오를 검증하기 전까지 운영 기준으로 단정하지 않는다.
```

## 설정 반영 판단

이번 결과만으로 `application.yml`의 운영 queue 설정은 변경하지 않는다.

이유는 다음과 같다.

- 이번 실험은 active 사용자만 seed하고 waiting queue 흐름은 포함하지 않았다.
- 요청률은 150 iterations/s로 고정했다.
- 단일 인스턴스, 현재 PC, Docker 기반 결과다.
- active 2000에서 병목이 드러나지 않아 실제 한계점은 아직 찾지 못했다.

따라서 현재 단계에서는 다음 판단이 적절하다.

| 설정 | 판단 |
| --- | --- |
| `queue.max-active-member=1000` | 유지 가능. 현재 환경에서는 보수적 안정 기준 |
| `queue.enter-batch-size=500` | 유지 가능. active 1000 기준 50% batch admission |
| active 1500~2000 | 확장 가능 후보. 대기열 포함 시나리오와 더 높은 요청률에서 재검증 필요 |

## 결과 파일

이번 실험의 k6 raw summary는 다음 파일에 저장됐다.

- `loadtest/k6/results/active-admission-300-150rps-2m-summary.txt`
- `loadtest/k6/results/active-admission-500-150rps-2m-summary.txt`
- `loadtest/k6/results/active-admission-1000-150rps-2m-summary.txt`
- `loadtest/k6/results/active-admission-1500-150rps-2m-summary.txt`
- `loadtest/k6/results/active-admission-2000-150rps-2m-summary.txt`
- 각 txt와 같은 prefix의 `summary.json`

`loadtest/k6/results`는 git ignore 대상이므로, 포트폴리오 근거로는 이 문서를 대표 결과로 사용한다.

## 다음 단계

이번 실험은 active 사용자 수별 reservation 처리 안정성을 확인했다. 다음 단계는 실제 대기열 흐름을 포함해야 한다.

추천 순서는 다음과 같다.

1. waiting 10,000명 + active batch 500명 조건으로 queue admission 동작 검증
2. active 1000 / 1500 / 2000 조건에서 요청률을 300 iterations/s 이상으로 올려 실제 한계점 탐색
3. `queue.enter-batch-size`를 250 / 500 / 1000으로 바꿔 admission batch가 Redis와 예약 요청에 주는 영향 비교

이 과정을 거치면 단순히 "active 1000명은 된다"가 아니라, "현재 장비에서 몇 명씩 active로 열고, 다음 batch를 얼마나 자주 열어야 하는지"까지 설명할 수 있다.
