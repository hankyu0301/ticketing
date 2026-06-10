# Queue / Reservation Load Test

이 디렉터리는 Redis queue, active token, 예약 생성 동시성, 좌석 경합 제어를 측정하기 위한 k6 부하테스트를 담는다.

회원가입과 일반 로그인 비용은 실제 부하 시나리오에서 제외한다. `loadtest` profile에서만 활성화되는 세션 발급 API를 사용해 Spring Security 세션을 빠르게 만들고, queue/reservation API 자체의 비용을 측정한다.

## 구성

- `ticketing-loadtest-base`: schema와 대용량 seed SQL이 적용된 기준 DB 볼륨
- `ticketing-loadtest-run`: 실제 테스트마다 base에서 복사해 사용하는 mutable DB 볼륨
- `ticketing-loadtest-mariadb`: k6 실행용 MariaDB
- `ticketing-loadtest-redis`: Spring Session, queue, lock에 사용하는 Redis
- `ticketing-loadtest-app`: `java -jar`로 실행되는 Spring Boot loadtest 앱

## Seed 데이터

[10_seed_large_base.sql](fixtures/mysql/10_seed_large_base.sql)은 `INSERT ... SELECT` 방식으로 데이터를 생성한다.

- 회원: 100,000명
- hot schedule 좌석: 50,000개
- history schedule 좌석: 50,000개
- 예약 이력: 300,000건
- 결제 이력: 240,000건

회원 비밀번호는 모두 `password1234!`이지만, 실제 부하 시나리오는 BCrypt 로그인을 쓰지 않는다.

## 실행 순서

1. 앱 이미지를 빌드한다.

```bash
./gradlew loadtestAppBuild
```

2. base DB를 최초 1회 생성한다.

```bash
./gradlew loadtestBaseRebuild
```

3. run DB를 base에서 복사하고 MariaDB, Redis, 앱 컨테이너를 띄운다.

```bash
./gradlew loadtestRunReset
```

4. seed row count를 확인한다.

```bash
./gradlew loadtestVerifyLargeSeed
```

5. k6 smoke를 실행한다.

```bash
./gradlew loadtestRunK6 -Pscenario=smoke-seeded-login -Ptargets=loadtest/k6/data/targets.smoke.json
```

## Loadtest Session

`loadtest` profile에서만 다음 API가 열린다.

```http
POST /api/v1/loadtest/sessions
Content-Type: application/json

{
  "email": "loadtest-user-020001@test.com"
}
```

이 API는 BCrypt 검증 없이 seed 회원을 조회해 Spring Security 세션을 만든다. 이후 k6는 `SESSION` 쿠키와 CSRF token으로 queue/reservation API를 호출한다.

운영 profile에서는 컨트롤러가 등록되지 않고, SecurityConfig에서도 `/api/v1/loadtest/**`가 허용되지 않는다.

## 시나리오

- `smoke-seeded-login`
  - 일반 로그인 흐름이 동작하는지 확인한다.
- `load-queue-enter`
  - loadtest 세션으로 queue enter 처리량과 entered/waiting 분포를 측정한다.
- `load-queue-rank-polling`
  - loadtest 세션으로 waiting 사용자의 rank 조회 비용을 측정한다.
- `load-reservation-unique`
  - VU별 세션을 한 번 만들고, 고유 좌석 예약 처리량을 측정한다.
- `stress-reservation-contention`
  - 여러 활성 사용자가 인기 좌석군에 몰릴 때 성공/비즈니스 실패를 분리해 측정한다.
  - `targets.*.json`의 `reservation.contentionZones` weight를 기준으로 front/middle/normal 구역에 요청을 분산한다.

## 자주 쓰는 명령

Queue enter:

```bash
./gradlew loadtestResetRedisQueue
./gradlew loadtestSeedRedisQueue -PactiveCount=0 -PwaitingCount=0
./gradlew loadtestRunK6 -Pscenario=load-queue-enter -Ptargets=loadtest/k6/data/targets.large.json -Pprofile=baseline
```

Rank polling:

```bash
./gradlew loadtestResetRedisQueue
./gradlew loadtestSeedRedisQueue -PactiveStartMemberId=1 -PactiveCount=1000 -PwaitingStartMemberId=1001 -PwaitingCount=19000
./gradlew loadtestRunK6 -Pscenario=load-queue-rank-polling -Ptargets=loadtest/k6/data/targets.large.json -Pprofile=baseline
```

고유 좌석 예약:

```bash
./gradlew loadtestResetHotSchedule
./gradlew loadtestResetRedisQueue
./gradlew loadtestSeedRedisQueue -PactiveStartMemberId=20001 -PactiveCount=10000 -PwaitingCount=0
./gradlew loadtestRunK6 -Pscenario=load-reservation-unique -Ptargets=loadtest/k6/data/targets.large.json -Pprofile=baseline
```

좌석 경합 예약:

```bash
./gradlew loadtestResetHotSchedule
./gradlew loadtestResetRedisQueue
./gradlew loadtestSeedRedisQueue -PactiveStartMemberId=30001 -PactiveCount=2000 -PwaitingCount=0
./gradlew loadtestRunK6 -Pscenario=stress-reservation-contention -Ptargets=loadtest/k6/data/targets.large.json -Pprofile=baseline
```

인기 좌석군 경합은 다음 설정을 사용한다.

| 구역 | 좌석 범위 | weight | 의미 |
| --- | --- | ---: | --- |
| `front` | `10001`부터 100석 | 60 | 가장 인기 있는 좌석군 |
| `middle` | `10101`부터 400석 | 30 | 중간 인기 좌석군 |
| `normal` | `10501`부터 1500석 | 10 | 일반 좌석군 |

weight는 요청 분포를 의미한다. 예를 들어 large target에서는 전체 예약 시도 중 약 60%가 `front`, 30%가 `middle`, 10%가 `normal` 좌석군으로 향한다. 좌석 선택은 랜덤이 아니라 iteration index 기반으로 결정되므로 같은 설정에서 재현 가능한 결과를 얻을 수 있다.

active admission 한계를 비교할 때는 같은 시나리오에 active 사용자 수와 k6 VU 상한을 함께 바꿔 실행한다.

```bash
./gradlew loadtestSeedRedisQueue -PactiveStartMemberId=30001 -PactiveCount=500 -PwaitingCount=0
./gradlew loadtestRunK6 -Pscenario=stress-reservation-contention -Ptargets=loadtest/k6/data/targets.large.json -Pprofile=baseline -PpreAllocatedVus=500 -PmaxVus=500
```

테스트 후에는 대상 좌석군에서 중복 예약이 없는지 확인한다.

```sql
SELECT seat_id, COUNT(*) AS reservation_count
FROM reservations
WHERE schedule_id = 1
GROUP BY seat_id
HAVING COUNT(*) > 1;
```

## 설정 Override

`loadtestRunK6`는 다음 Gradle property를 k6 환경변수로 넘긴다.

- `-Pscenario`: 실행할 시나리오 이름
- `-Ptargets`: target JSON 경로
- `-Pprofile`: `smoke`, `baseline`, `stress`
- `-Presult`: 결과 파일 prefix
- `-Prate`, `-Pduration`, `-Pvus`, `-Piterations`, `-PmaxVus`, `-PpreAllocatedVus`, `-PmaxDuration`: k6 실행 파라미터 override

## 결과

summary는 기본적으로 `loadtest/k6/results/{scenario}-summary.json`과 `loadtest/k6/results/{scenario}-summary.txt`에 생성된다.

`loadtest/k6/results/`는 `.gitignore` 대상이다. 성능 개선 근거로 남길 요약 문서는 별도 `docs/performance` 문서에 정리하는 편을 권장한다.
