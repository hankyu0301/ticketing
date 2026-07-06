package com.ticketing.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;

import static org.assertj.core.api.Assertions.assertThat;

class RedissonSingleServerConfigTest {

    @Test
    @DisplayName("Standalone 설정은 단일 Redis 서버를 사용한다")
    void configureSingleServer() throws Exception {
        RedisProperties properties = new RedisProperties();
        properties.setHost("localhost");
        properties.setPort(6379);
        properties.setPassword("redis-password");

        String yaml = new RedissonSingleServerConfig(properties).redissonConfiguration().toYAML();

        assertThat(yaml)
                .contains("singleServerConfig:")
                .contains("redis://localhost:6379")
                .contains("password: \"redis-password\"");
    }
}
