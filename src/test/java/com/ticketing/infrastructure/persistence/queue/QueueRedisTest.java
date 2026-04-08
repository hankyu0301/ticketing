package com.ticketing.infrastructure.persistence.queue;

import com.ticketing.infrastructure.persistence.queue.QueueRedisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Set;

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
    }

    @Test
    @DisplayName("active가 비어 있으면 대기 없이 즉시 입장한다.")
    void logicEnterFreePass() {
        Long memberId = 100L;

        Long result = queueRedisRepository.register(SCHEDULE_ID, memberId, TEST_MAX_ACTIVE, 300000);

        assertThat(result).isEqualTo(-100L);
        assertThat(isMemberInActive(memberId)).isTrue();
    }

    @Test
    @DisplayName("이미 active인 사용자는 재진입해도 active 상태를 유지하고 대기열에 중복 등록되지 않는다.")
    void logicEnterActiveMemberAgain() {
        Long memberId = 100L;
        queueRedisRepository.register(SCHEDULE_ID, memberId, TEST_MAX_ACTIVE, 300000);

        Long result = queueRedisRepository.register(SCHEDULE_ID, memberId, TEST_MAX_ACTIVE, 300000);

        assertThat(result).isEqualTo(-100L);
        assertThat(zCard(getActiveKey())).isEqualTo(1L);
        assertThat(zCard(getWaitKey())).isEqualTo(0L);
    }

    @Test
    @DisplayName("active가 가득 차면 사용자는 대기열에 들어가고 순번을 받는다.")
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
    @DisplayName("이미 대기열에 있는 사용자는 재진입해도 기존 순번을 반환하고 중복 등록되지 않는다.")
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
    @DisplayName("스케줄러는 빈자리가 생기면 대기열 사용자를 active로 이동시킨다.")
    void logicSchedulerMove() {
        long tinyMax = 1L;
        queueRedisRepository.register(SCHEDULE_ID, 100L, tinyMax, 300000);
        queueRedisRepository.register(SCHEDULE_ID, 200L, tinyMax, 300000);

        queueRedisRepository.removeActiveMember(SCHEDULE_ID, 100L);

        List<String> moved = queueRedisRepository.moveUsersToActive(SCHEDULE_ID, tinyMax, 10, 300000);

        assertThat(moved).hasSize(1);
        assertThat(moved.get(0)).isEqualTo("200");
        assertThat(isMemberInActive(200L)).isTrue();
    }

    @Test
    @DisplayName("processing schedule에 중복으로 추가해도 하나만 저장된다.")
    void logicManageSet() {
        queueRedisRepository.addProcessingSchedule(SCHEDULE_ID);
        queueRedisRepository.addProcessingSchedule(SCHEDULE_ID);
        queueRedisRepository.addProcessingSchedule(SCHEDULE_ID);

        Set<Long> schedules = queueRedisRepository.getProcessingSchedules();
        assertThat(schedules).hasSize(1);
        assertThat(schedules).contains(SCHEDULE_ID);
    }

    @Test
    @DisplayName("wait와 active가 모두 비면 processing schedule에서 자동으로 제거된다.")
    void logicCleanupProcessingScheduleWhenQueueDrained() {
        long tinyMax = 1L;
        queueRedisRepository.addProcessingSchedule(SCHEDULE_ID);
        queueRedisRepository.register(SCHEDULE_ID, 100L, tinyMax, 300000);
        queueRedisRepository.register(SCHEDULE_ID, 200L, tinyMax, 300000);

        queueRedisRepository.removeActiveMember(SCHEDULE_ID, 100L);
        queueRedisRepository.moveUsersToActive(SCHEDULE_ID, tinyMax, 10, 300000);
        queueRedisRepository.removeActiveMember(SCHEDULE_ID, 200L);

        Set<Long> schedules = queueRedisRepository.getProcessingSchedules();
        assertThat(schedules).doesNotContain(SCHEDULE_ID);
    }

    @Test
    @DisplayName("만료된 active 토큰은 상태 조회 시 제거되고 not found로 처리된다.")
    void logicStatusExpiredActiveRemoved() {
        Long memberId = 777L;
        redisTemplate.opsForZSet().add(getActiveKey(), memberId.toString(), System.currentTimeMillis() - 1_000);

        Long result = queueRedisRepository.getRank(SCHEDULE_ID, memberId);

        assertThat(result).isEqualTo(-1L);
        assertThat(isMemberInActive(memberId)).isFalse();
    }

    private boolean isMemberInActive(Long memberId) {
        return redisTemplate.opsForZSet().score(getActiveKey(), memberId.toString()) != null;
    }

    private boolean isMemberInWait(Long memberId) {
        return redisTemplate.opsForZSet().score(getWaitKey(), memberId.toString()) != null;
    }

    private Long zCard(String key) {
        return redisTemplate.opsForZSet().zCard(key);
    }

    private String getActiveKey() {
        return "queue:{" + SCHEDULE_ID + "}:active";
    }

    private String getWaitKey() {
        return "queue:{" + SCHEDULE_ID + "}:wait";
    }
}
