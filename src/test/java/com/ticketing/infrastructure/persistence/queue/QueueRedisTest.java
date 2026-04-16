package com.ticketing.infrastructure.persistence.queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class QueueRedisTest {

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:alpine"))
            .withExposedPorts(6379);

    @Autowired
    private QueueRedisRepository queueRedisRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final Long SCHEDULE_ID = 1L;
    private static final long TEST_MAX_ACTIVE = 10L;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        ReflectionTestUtils.setField(queueRedisRepository, "maxActiveMember", TEST_MAX_ACTIVE);
        saveOpenedScheduleMeta();
    }

    @Test
    @DisplayName("queue meta hash를 저장하고 다시 조회한다")
    void saveAndFindScheduleMeta() {
        long now = System.currentTimeMillis();

        queueRedisRepository.saveScheduleMeta(SCHEDULE_ID, now - 1_000, now + 60_000);

        QueueScheduleMeta meta = queueRedisRepository.findScheduleMeta(SCHEDULE_ID).orElseThrow();

        assertThat(meta.scheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(meta.reservationOpenAtMs()).isEqualTo(now - 1_000);
        assertThat(meta.concertDateMs()).isEqualTo(now + 60_000);
    }

    @Test
    @DisplayName("meta가 없으면 enter script는 meta missing 코드를 반환한다")
    void logicEnterFailsWhenMetaMissing() {
        redisTemplate.delete(getMetaKey());

        Long result = queueRedisRepository.register(SCHEDULE_ID, 100L, TEST_MAX_ACTIVE, 300000);

        assertThat(result).isEqualTo(-2L);
    }

    @Test
    @DisplayName("예매 오픈 전이면 enter script는 reservation not open 코드를 반환한다")
    void logicEnterFailsWhenReservationNotOpen() {
        long now = System.currentTimeMillis();
        queueRedisRepository.saveScheduleMeta(SCHEDULE_ID, now + 60_000, now + 86_400_000);

        Long result = queueRedisRepository.register(SCHEDULE_ID, 100L, TEST_MAX_ACTIVE, 300000);

        assertThat(result).isEqualTo(-3L);
    }

    @Test
    @DisplayName("예매 오픈 전이면 status script는 reservation not open 코드를 반환한다")
    void logicStatusFailsWhenReservationNotOpen() {
        long now = System.currentTimeMillis();
        queueRedisRepository.saveScheduleMeta(SCHEDULE_ID, now + 60_000, now + 86_400_000);

        Long result = queueRedisRepository.getRank(SCHEDULE_ID, 100L);

        assertThat(result).isEqualTo(-3L);
    }

    @Test
    @DisplayName("active가 비어 있으면 대기 없이 즉시 입장한다")
    void logicEnterFreePass() {
        Long memberId = 100L;

        Long result = queueRedisRepository.register(SCHEDULE_ID, memberId, TEST_MAX_ACTIVE, 300000);

        assertThat(result).isEqualTo(-100L);
        assertThat(isMemberInActive(memberId)).isTrue();
    }

    @Test
    @DisplayName("이미 active인 사용자는 즉시 입장 상태를 유지한다")
    void logicEnterActiveMemberAgain() {
        Long memberId = 100L;
        queueRedisRepository.register(SCHEDULE_ID, memberId, TEST_MAX_ACTIVE, 300000);

        Long result = queueRedisRepository.register(SCHEDULE_ID, memberId, TEST_MAX_ACTIVE, 300000);

        assertThat(result).isEqualTo(-100L);
        assertThat(zCard(getActiveKey())).isEqualTo(1L);
        assertThat(zCard(getWaitKey())).isEqualTo(0L);
    }

    @Test
    @DisplayName("active가 가득 차면 waiting queue에 넣고 rank를 반환한다")
    void logicEnterWait() {
        for (long i = 1; i <= TEST_MAX_ACTIVE; i++) {
            queueRedisRepository.register(SCHEDULE_ID, i, TEST_MAX_ACTIVE, 300000);
        }

        Long memberId = 999L;
        Long result = queueRedisRepository.register(SCHEDULE_ID, memberId, TEST_MAX_ACTIVE, 300000);

        assertThat(result).isEqualTo(0L);
        assertThat(isMemberInWait(memberId)).isTrue();
    }

    @Test
    @DisplayName("이미 waiting인 사용자는 기존 rank를 유지한다")
    void logicEnterWaitingMemberAgain() {
        for (long i = 1; i <= TEST_MAX_ACTIVE; i++) {
            queueRedisRepository.register(SCHEDULE_ID, i, TEST_MAX_ACTIVE, 300000);
        }

        Long memberId = 999L;
        queueRedisRepository.register(SCHEDULE_ID, memberId, TEST_MAX_ACTIVE, 300000);

        Long result = queueRedisRepository.register(SCHEDULE_ID, memberId, TEST_MAX_ACTIVE, 300000);

        assertThat(result).isEqualTo(0L);
        assertThat(zCard(getWaitKey())).isEqualTo(1L);
        assertThat(isMemberInWait(memberId)).isTrue();
    }

    @Test
    @DisplayName("scheduler가 자리를 비우면 waiting 사용자를 active로 이동시킨다")
    void logicSchedulerMove() {
        long tinyMax = 1L;
        ReflectionTestUtils.setField(queueRedisRepository, "maxActiveMember", tinyMax);
        queueRedisRepository.register(SCHEDULE_ID, 100L, tinyMax, 300000);
        queueRedisRepository.register(SCHEDULE_ID, 200L, tinyMax, 300000);

        queueRedisRepository.removeActiveMember(SCHEDULE_ID, 100L);

        List<String> moved = queueRedisRepository.moveUsersToActive(SCHEDULE_ID, tinyMax, 10, 300000);

        assertThat(moved).hasSize(1);
        assertThat(moved.get(0)).isEqualTo("200");
        assertThat(isMemberInActive(200L)).isTrue();
    }

    @Test
    @DisplayName("waiting이 있고 active가 가득 차 있으면 processing schedule은 다음 만료 시각으로 예약된다")
    void logicScheduleFutureWhenActiveIsFull() {
        long tinyMax = 1L;
        ReflectionTestUtils.setField(queueRedisRepository, "maxActiveMember", tinyMax);
        queueRedisRepository.register(SCHEDULE_ID, 100L, tinyMax, 300000);
        queueRedisRepository.register(SCHEDULE_ID, 200L, tinyMax, 300000);

        queueRedisRepository.refreshProcessingSchedule(SCHEDULE_ID);

        Double activeExpireAt = activeScore(100L);
        Double processingScore = processingScore();

        assertThat(processingScore).isEqualTo(activeExpireAt);
        assertThat(queueRedisRepository.getDueProcessingSchedules(System.currentTimeMillis()))
                .doesNotContain(SCHEDULE_ID);
    }

    @Test
    @DisplayName("같은 schedule을 여러 번 refresh해도 due schedule은 중복 없이 관리된다")
    void logicManageDueScheduleWithoutDuplicates() {
        long tinyMax = 1L;
        ReflectionTestUtils.setField(queueRedisRepository, "maxActiveMember", tinyMax);
        queueRedisRepository.register(SCHEDULE_ID, 100L, tinyMax, 300000);
        queueRedisRepository.register(SCHEDULE_ID, 200L, tinyMax, 300000);

        queueRedisRepository.removeActiveMember(SCHEDULE_ID, 100L);
        queueRedisRepository.refreshProcessingSchedule(SCHEDULE_ID);

        List<Long> dueSchedules = queueRedisRepository.getDueProcessingSchedules(System.currentTimeMillis());
        assertThat(dueSchedules).hasSize(1);
        assertThat(dueSchedules).contains(SCHEDULE_ID);
    }

    @Test
    @DisplayName("wait와 active가 모두 비면 processing schedule에서 제거된다")
    void logicRemoveProcessingScheduleWhenQueueDrained() {
        long tinyMax = 1L;
        ReflectionTestUtils.setField(queueRedisRepository, "maxActiveMember", tinyMax);
        queueRedisRepository.register(SCHEDULE_ID, 100L, tinyMax, 300000);
        queueRedisRepository.register(SCHEDULE_ID, 200L, tinyMax, 300000);

        queueRedisRepository.refreshProcessingSchedule(SCHEDULE_ID);
        queueRedisRepository.removeActiveMember(SCHEDULE_ID, 100L);
        queueRedisRepository.moveUsersToActive(SCHEDULE_ID, tinyMax, 10, 300000);
        queueRedisRepository.removeActiveMember(SCHEDULE_ID, 200L);

        assertThat(processingScore()).isNull();
    }

    @Test
    @DisplayName("만료된 active 토큰은 status 조회 중 제거되고 not found를 반환한다")
    void logicStatusExpiredActiveRemoved() {
        Long memberId = 777L;
        redisTemplate.opsForZSet().add(getActiveKey(), memberId.toString(), System.currentTimeMillis() - 1_000);

        Long result = queueRedisRepository.getRank(SCHEDULE_ID, memberId);

        assertThat(result).isEqualTo(-1L);
        assertThat(isMemberInActive(memberId)).isFalse();
    }

    @Test
    @DisplayName("active leave 후 waiting이 남아 있으면 processing schedule은 즉시 처리 대상으로 갱신된다")
    void logicScheduleNowWhenActiveLeaveOpensSlot() {
        long tinyMax = 1L;
        ReflectionTestUtils.setField(queueRedisRepository, "maxActiveMember", tinyMax);
        queueRedisRepository.register(SCHEDULE_ID, 100L, tinyMax, 300000);
        queueRedisRepository.register(SCHEDULE_ID, 200L, tinyMax, 300000);
        queueRedisRepository.refreshProcessingSchedule(SCHEDULE_ID);

        queueRedisRepository.removeActiveMember(SCHEDULE_ID, 100L);

        assertThat(isMemberInActive(100L)).isFalse();
        assertThat(isMemberInWait(200L)).isTrue();
        assertThat(queueRedisRepository.getDueProcessingSchedules(System.currentTimeMillis())).contains(SCHEDULE_ID);
        assertThat(processingScore()).isLessThanOrEqualTo((double) System.currentTimeMillis());
    }

    private void saveOpenedScheduleMeta() {
        long now = System.currentTimeMillis();
        queueRedisRepository.saveScheduleMeta(SCHEDULE_ID, now - 60_000, now + 86_400_000);
    }

    private boolean isMemberInActive(Long memberId) {
        return redisTemplate.opsForZSet().score(getActiveKey(), memberId.toString()) != null;
    }

    private boolean isMemberInWait(Long memberId) {
        return redisTemplate.opsForZSet().score(getWaitKey(), memberId.toString()) != null;
    }

    private Double activeScore(Long memberId) {
        return redisTemplate.opsForZSet().score(getActiveKey(), memberId.toString());
    }

    private Double processingScore() {
        return redisTemplate.opsForZSet().score(getProcessingKey(), SCHEDULE_ID.toString());
    }

    private Long zCard(String key) {
        return redisTemplate.opsForZSet().zCard(key);
    }

    private String getMetaKey() {
        return "queue:{" + SCHEDULE_ID + "}:meta";
    }

    private String getActiveKey() {
        return "queue:{" + SCHEDULE_ID + "}:active";
    }

    private String getWaitKey() {
        return "queue:{" + SCHEDULE_ID + "}:wait";
    }

    private String getProcessingKey() {
        return "queue:processing_schedules";
    }
}
