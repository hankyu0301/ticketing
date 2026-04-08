package com.ticketing.global.config;

import com.ticketing.infrastructure.persistence.queue.QueueRedisRepository;
import com.ticketing.application.queue.scheduler.QueueScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Testcontainers
// 테스트 중 스케줄러 자동 실행 방지
@TestPropertySource(properties = "scheduler.fixed-delay=100000000")
class ShedLockTest {

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:alpine"))
            .withExposedPorts(6379);

    @Autowired
    private QueueScheduler queueScheduler;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @MockitoSpyBean
    private QueueRedisRepository queueRedisRepository;

    private static final String PROCESSING_SCHEDULES_KEY = "queue:processing_schedules";

    @BeforeEach
    void setUp() {
        redisConnectionFactory.getConnection().serverCommands().flushAll();
        Mockito.reset(queueRedisRepository);
    }

    @Test
    @DisplayName("다른 서버가 이미 락을 잡고 있으면 로직은 실행되지 않는다.")
    void distributedLockSimulation() {
        // given
        // (ShedLock 라이브러리가 기본적으로 "job-lock" 접두사를 붙임)
        String lockKey = "job-lock:ticketing-lock:QueueScheduler_processQueue";

        // Server B가 먼저 락을 선점했다고 가정
        redisTemplate.opsForValue().set(lockKey, "SERVER_B_HOST", Duration.ofSeconds(10));

        redisTemplate.opsForSet().add(PROCESSING_SCHEDULES_KEY, "1");

        // when: 현재 서버(Server A)가 실행 시도
        queueScheduler.processQueue();

        // then: 락이 걸려있으므로 실행 0회
        verify(queueRedisRepository, times(0))
                .moveUsersToActive(anyLong(), anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("락이 없으면 로직이 실행된다.")
    void normalExecutionTest() {
        // given
        redisTemplate.opsForSet().add(PROCESSING_SCHEDULES_KEY, "1");

        // when
        queueScheduler.processQueue();

        // then
        verify(queueRedisRepository, times(1))
                .moveUsersToActive(anyLong(), anyLong(), anyLong(), anyLong());
    }
}
