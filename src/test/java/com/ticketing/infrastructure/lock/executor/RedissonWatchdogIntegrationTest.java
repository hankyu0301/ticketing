package com.ticketing.infrastructure.lock.executor;

import com.ticketing.support.IntegrationTestSupport;
import org.junit.jupiter.api.*;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("Redisson WatchDog 동작 확인용 통합 테스트")
class RedissonWatchdogIntegrationTest extends IntegrationTestSupport {

    private static final String WATCHDOG_LOCK_NAME = "ticketing:test:watchdog";
    private static final String EXPLICIT_LEASE_LOCK_NAME = "ticketing:test:explicit-lease";
    private static final long WATCHDOG_TIMEOUT_MS = 3_000L;
    private static final long SUPPLIER_WORK_MS = 4_500L;

    @Autowired
    private RedisProperties redisProperties;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private RedissonClient redissonClient;
    private RedissonLockExecutor lockExecutor;

    @BeforeEach
    void setUp() {
        redissonClient = Redisson.create(redissonConfig());
        lockExecutor = new RedissonLockExecutor(redissonClient);
        ReflectionTestUtils.setField(lockExecutor, "waitTimeMs", 500L);
    }

    @AfterEach
    void tearDown() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
    }

    @Test
    @DisplayName("leaseTime 없는 Redisson 락은 supplier 실행 중 WatchDog로 TTL을 갱신한다")
    void renewLockTtlWhileSupplierRunsLongerThanWatchdogTimeout() {
        AtomicBoolean ttlRenewed = new AtomicBoolean(false);
        List<Long> ttlSamples = new ArrayList<>();

        String result = lockExecutor.execute(WATCHDOG_LOCK_NAME, () -> {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SUPPLIER_WORK_MS);
            long previousTtl = currentTtl(WATCHDOG_LOCK_NAME);

            while (System.nanoTime() < deadline) {
                long ttl = currentTtl(WATCHDOG_LOCK_NAME);
                ttlSamples.add(ttl);
                if (ttl > previousTtl + 400L) {
                    ttlRenewed.set(true);
                }
                previousTtl = ttl;
                sleep(200L);
            }

            assertThat(ttlSamples).allMatch(ttl -> ttl > 0L);
            assertThat(ttlRenewed).isTrue();
            return "reserved";
        });

        assertThat(result).isEqualTo("reserved");
        assertThat(redisTemplate.hasKey(WATCHDOG_LOCK_NAME)).isFalse();
    }

    @Test
    @DisplayName("명시적 leaseTime을 사용하면 WatchDog가 갱신하지 않아 lease 이후 락이 만료된다")
    void expireLockWhenExplicitLeaseTimeIsShorterThanWorkTime() throws InterruptedException {
        RLock lock = redissonClient.getLock(EXPLICIT_LEASE_LOCK_NAME);

        try {
            assertThat(lock.tryLock(500L, 1_200L, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(currentTtl(EXPLICIT_LEASE_LOCK_NAME)).isPositive();

            Thread.sleep(2_000L);

            assertThat(redisTemplate.hasKey(EXPLICIT_LEASE_LOCK_NAME)).isFalse();
            assertThat(lock.isHeldByCurrentThread()).isFalse();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private Config redissonConfig() {
        Config config = new Config();
        config.setLockWatchdogTimeout(WATCHDOG_TIMEOUT_MS);
        var singleServer = config.useSingleServer()
                .setAddress("redis://" + redisProperties.getHost() + ":" + redisProperties.getPort());

        if (StringUtils.hasText(redisProperties.getPassword())) {
            singleServer.setPassword(redisProperties.getPassword());
        }

        return config;
    }

    private long currentTtl(String lockName) {
        Long ttl = redisTemplate.getExpire(lockName, TimeUnit.MILLISECONDS);
        assertThat(ttl).isNotNull();
        return ttl;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("TTL sampling was interrupted.", e);
        }
    }
}
