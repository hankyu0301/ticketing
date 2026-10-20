# k6 부하테스트 실행 가이드

고유 좌석 예약의 처리량과 좌석 경합을 재현하는 가이드입니다. [docker-compose.loadtest.yml](../docker-compose.loadtest.yml)로 MariaDB 10.6·Redis 7·애플리케이션을 실행합니다.

아래 실험은 사용자를 Redis active set에 미리 등록하고, 전용 세션 API로 인증을 준비합니다. 예약 요청의 세션·CSRF·active 검증은 포함하며, 대기열 입장 대기·BCrypt 로그인·결제 처리량은 측정에서 제외합니다.

## 준비

Java 17, Docker Compose v2, k6, Bash 환경이 필요합니다. 모든 명령은 **저장소 루트**에서 실행합니다.

```bash
java -version
docker compose version
k6 version
```

사용 포트는 `8080`, `3306`, `6379`입니다. 같은 포트를 사용하는 개발용 Compose는 중지한 상태에서 실행합니다.

## 최초 환경 구성

1. 앱 이미지를 빌드하고 기준 DB를 생성합니다.

```bash
bash ./gradlew loadtestAppBuild
bash ./gradlew loadtestBaseRebuild
```

기준 DB에는 회원 10만 명, 회차별 좌석 5만 석씩 2개 회차, 예약 이력 30만 건, 결제 이력 24만 건을 생성합니다. `loadtestBaseRebuild`는 기존 기준 볼륨을 삭제하고 다시 만들므로 최초 실행이나 schema·seed 변경 시 사용합니다.

2. 기준 DB의 seed 생성이 끝나 `healthy`가 될 때까지 확인합니다.

```bash
docker compose -f docker-compose.loadtest.yml --profile base ps mariadb-base
```

3. 기준 DB를 실행용 DB로 복사하고 애플리케이션을 시작합니다.

```bash
bash ./gradlew loadtestRunReset
bash ./gradlew loadtestVerifyLargeSeed
curl --fail http://localhost:8080/actuator/health
```

기준 볼륨 `ticketing-loadtest-base`는 반복 실험의 초기 데이터로 보관하고, 실험은 `ticketing-loadtest-run`에서 수행합니다. `loadtestRunReset`은 실행용 DB를 기준 데이터로 덮어씁니다.

## 대표 실험

각 예시의 초기화 명령부터 실행합니다. `loadtestResetHotSchedule`은 회차 1의 예약·결제를 삭제하고 좌석을 해제하며, `loadtestResetRedisQueue`는 대기열·active·예약 불가 캐시를 초기화합니다.

### 고유 좌석 예약: 초당 500회·60초

[targets.large.json](./k6/data/targets.large.json)의 `rps500Long`을 사용합니다. 서로 다른 좌석에 예약을 요청하며, 목표 요청 수는 30,000건입니다.

```bash
bash ./gradlew loadtestResetHotSchedule
bash ./gradlew loadtestResetRedisQueue
bash ./gradlew loadtestSeedRedisQueue \
  -PactiveStartMemberId=20001 -PactiveCount=1000 -PwaitingCount=0
bash ./gradlew loadtestRunK6 \
  -Pscenario=load-reservation-unique \
  -Ptargets=loadtest/k6/data/targets.large.json \
  -Pprofile=rps500Long \
  -PreservationExecutor=constant-arrival-rate
```

`-PreservationExecutor`를 지정해야 고정 요청률로 실행됩니다. 예약 성공 건수·p95와 함께 `dropped_iterations`를 확인합니다.

### 인기 좌석 500석 경합: 초당 150회·2분

[targets.contention-500.json](./k6/data/targets.contention-500.json)의 `baseline`을 사용합니다. 좌석 500석을 50·150·300석으로 나누고 요청 비중을 60·30·10으로 설정합니다.

```bash
bash ./gradlew loadtestResetHotSchedule
bash ./gradlew loadtestResetRedisQueue
bash ./gradlew loadtestSeedRedisQueue \
  -PactiveStartMemberId=30001 -PactiveCount=2000 -PwaitingCount=0
bash ./gradlew loadtestRunK6 \
  -Pscenario=stress-reservation-contention \
  -Ptargets=loadtest/k6/data/targets.contention-500.json \
  -Pprofile=baseline
```

목표 요청 수는 18,000건입니다. 성공 가능한 좌석 수와 실제 예약 건수, 중복 예약 여부를 확인합니다.

### 단일 좌석 경합: 300명이 한 번씩 요청

빠른 실패의 지연을 비교할 때는 [targets.burst-contention.json](./k6/data/targets.burst-contention.json)의 `burst300`을 사용합니다.

```bash
bash ./gradlew loadtestResetHotSchedule
bash ./gradlew loadtestResetRedisQueue
bash ./gradlew loadtestSeedRedisQueue \
  -PactiveStartMemberId=40001 -PactiveCount=500 -PwaitingCount=0
bash ./gradlew loadtestRunK6 \
  -Pscenario=burst-reservation-contention \
  -Ptargets=loadtest/k6/data/targets.burst-contention.json \
  -Pprofile=burst300
```

캐시·락 대기 시간의 비교 조건과 과거 측정값은 아래 성능 문서에서 확인합니다.

## 결과 확인

기본 결과 경로는 `loadtest/k6/results/{scenario}-summary.json`과 `{scenario}-summary.txt`입니다. 실행마다 기록을 나누려면 `loadtestRunK6`에 `-Presult=loadtest/k6/results/실험명`을 추가합니다. 원본 결과 폴더는 Git 제외 대상입니다.

| 확인 항목 | 의미 |
| --- | --- |
| `reservation_duration_ms` | 예약 응답 지연. 성공·경쟁 실패의 개별 duration 지표도 함께 확인 |
| `reservation_success_rate` | 예약 성공 비율 |
| `reservation_business_failure_rate` · `reservation_lock_timeout_rate` | 경쟁 실패 `400`과 락 대기 초과 `429`의 비율 |
| `reservation_unexpected_failure_rate` | 예상하지 않은 예약 실패 비율 |
| `dropped_iterations` | 예정된 반복을 시작하지 못한 횟수 |

경합에서 `400`·`429`는 허용한 응답이므로 `http_req_failed`만으로 장애율을 판단하지 않습니다.

DB 반영 건수와 좌석 상태는 다음 명령으로 확인합니다.

```bash
bash ./gradlew loadtestVerifyLargeSeed
```

DB에서 아래 SQL로 같은 회차·좌석의 결제 대기 또는 확정 예약이 겹치는지 확인합니다. 결과 행이 없어야 합니다.

```sql
SELECT seat_id, COUNT(*) AS reservation_count
FROM reservations
WHERE schedule_id = 1
  AND status IN ('PENDING', 'CONFIRMED')
GROUP BY seat_id
HAVING COUNT(*) > 1;
```

## 종료·문제 확인

```bash
bash ./gradlew loadtestRunDown
```

컨테이너를 종료하고 기준·실행 DB 볼륨은 유지합니다. 기동 문제는 다음 명령으로 확인합니다.

```bash
docker compose -f docker-compose.loadtest.yml ps
docker compose -f docker-compose.loadtest.yml logs --tail=100 app mariadb redis
```

## 성능 분석 문서

- [예약 부하테스트로 Tomcat·Hikari 병목 분석하기](../docs/performance/reservation-baseline.md)
- [좌석 경합을 분석하고 빠른 실패로 예약 지연 줄이기](../docs/performance/reservation-fast-fail.md)
