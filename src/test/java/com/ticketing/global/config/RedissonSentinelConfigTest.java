package com.ticketing.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedissonSentinelConfigTest {

    @Test
    @DisplayName("Dev 설정은 Master 전용 Sentinel 모드를 사용한다")
    void configureSentinelServers() throws Exception {
        RedisProperties properties = sentinelProperties(
                "ticketing-master",
                List.of(
                        "localhost:26379",
                        "redis://localhost:26380",
                        "localhost:26381"
                )
        );

        String yaml = new RedissonSentinelConfig(properties).redissonConfiguration().toYAML();

        assertThat(yaml)
                .contains("sentinelServersConfig:")
                .contains("masterName: \"ticketing-master\"")
                .contains("redis://localhost:26379")
                .contains("redis://localhost:26380")
                .contains("redis://localhost:26381")
                .contains("readMode: \"MASTER\"")
                .contains("subscriptionMode: \"MASTER\"")
                .contains("password: \"redis-password\"")
                .contains("sentinelPassword: \"sentinel-password\"");
    }

    @Test
    @DisplayName("Sentinel Master가 없으면 설정 오류로 처리한다")
    void rejectEmptySentinelMaster() {
        RedisProperties properties = sentinelProperties(null, List.of("localhost:26379"));

        assertThatThrownBy(() -> new RedissonSentinelConfig(properties).redissonConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Redis Sentinel master must not be empty.");
    }

    @Test
    @DisplayName("Sentinel 노드가 없으면 설정 오류로 처리한다")
    void rejectEmptySentinelNodes() {
        RedisProperties properties = sentinelProperties("ticketing-master", List.of());

        assertThatThrownBy(() -> new RedissonSentinelConfig(properties).redissonConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Redis Sentinel nodes must not be empty.");
    }

    private RedisProperties sentinelProperties(String master, List<String> nodes) {
        RedisProperties properties = new RedisProperties();
        properties.setPassword("redis-password");

        RedisProperties.Sentinel sentinel = new RedisProperties.Sentinel();
        sentinel.setMaster(master);
        sentinel.setNodes(nodes);
        sentinel.setPassword("sentinel-password");
        properties.setSentinel(sentinel);

        return properties;
    }
}
