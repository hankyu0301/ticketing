package com.ticketing.domain.queue;

import com.ticketing.domain.queue.repository.QueueRedisRepository;
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
    @DisplayName("활성열이 비어있으면 -> 대기 없이 즉시 입장 (Free Pass)")
    void logic_enter_freePass() {
        // given
        Long memberId = 100L;
        // when
        Long result = queueRedisRepository.register(SCHEDULE_ID, memberId, TEST_MAX_ACTIVE, 300000);
        // then
        assertThat(result).isEqualTo(-100L);
        assertThat(isMemberInActive(memberId)).isTrue();
    }

    @Test
    @DisplayName("활성열이 꽉 찼으면 -> 대기열 진입 및 순번 발급")
    void logic_enter_wait() {
        // given: 정원(10명)을 채움
        for (long i = 1; i <= TEST_MAX_ACTIVE; i++) {
            queueRedisRepository.register(SCHEDULE_ID, i, TEST_MAX_ACTIVE, 300000);
        }

        // when: 11번째 유저 입장
        Long memberId = 999L;
        Long result = queueRedisRepository.register(SCHEDULE_ID, memberId, TEST_MAX_ACTIVE, 300000);

        // then: 대기열 1등(index 0)이어야 함
        assertThat(result).isEqualTo(0L);
        assertThat(isMemberInWait(memberId)).isTrue();
    }

    @Test
    @DisplayName("스케줄러 이동: 빈 자리가 생기면 대기자를 활성열로 이동")
    void logic_scheduler_move() {
        // given: 정원 1명으로 제한하여 테스트
        long tinyMax = 1L;
        queueRedisRepository.register(SCHEDULE_ID, 100L, tinyMax, 300000); // Active
        queueRedisRepository.register(SCHEDULE_ID, 200L, tinyMax, 300000); // Wait

        // 강제 퇴장 (빈자리 생성)
        queueRedisRepository.removeActiveMember(SCHEDULE_ID, 100L);

        // when: 스케줄러 동작
        List<String> moved = queueRedisRepository.moveUsersToActive(SCHEDULE_ID, tinyMax, 10, 300000);

        // then
        assertThat(moved).hasSize(1);
        assertThat(moved.get(0)).isEqualTo("200");
        assertThat(isMemberInActive(200L)).isTrue();
    }

    @Test
    @DisplayName("스케줄 관리 등록: 중복 호출해도 Set에는 하나만 존재해야 함")
    void logic_manage_set() {
        // given & when
        queueRedisRepository.addProcessingSchedule(SCHEDULE_ID);
        queueRedisRepository.addProcessingSchedule(SCHEDULE_ID);
        queueRedisRepository.addProcessingSchedule(SCHEDULE_ID);

        // then
        Set<Long> schedules = queueRedisRepository.getProcessingSchedules();
        assertThat(schedules).hasSize(1);
        assertThat(schedules).contains(SCHEDULE_ID);
    }

    private boolean isMemberInActive(Long memberId) {
        return redisTemplate.opsForZSet().score("queue:active:" + SCHEDULE_ID, memberId.toString()) != null;
    }

    private boolean isMemberInWait(Long memberId) {
        return redisTemplate.opsForZSet().score("queue:wait:" + SCHEDULE_ID, memberId.toString()) != null;
    }
}