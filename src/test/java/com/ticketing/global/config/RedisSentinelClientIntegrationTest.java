package com.ticketing.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "REDIS_HA_IT", matches = "true")
class RedisSentinelClientIntegrationTest {

    @Test
    @DisplayName("Dev 프로필의 Lettuce와 Redisson이 Sentinel을 통해 현재 Primary를 사용한다")
    void connectLettuceAndRedissonThroughSentinel() throws Exception {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(SentinelTestConfig.class)
                .profiles("dev")
                .web(WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off")
                .run()) {
            assertThat(context.getEnvironment().getActiveProfiles()).containsExactly("dev");
            assertThat(context.getBeansOfType(RedissonSentinelConfig.class)).hasSize(1);
            assertThat(context.getBeansOfType(RedissonSingleServerConfig.class)).isEmpty();

            RedisProperties redisProperties = context.getBean(RedisProperties.class);
            verifyLettuceConnection(redisProperties);
            verifyRedissonConnection(context.getBean(RedissonClient.class));
        }
    }

    private void verifyLettuceConnection(RedisProperties redisProperties) {
        RedisProperties.Sentinel sentinelProperties = redisProperties.getSentinel();
        RedisSentinelConfiguration sentinel = new RedisSentinelConfiguration()
                .master(sentinelProperties.getMaster());
        sentinelProperties.getNodes().forEach(node -> addSentinelNode(sentinel, node));
        sentinel.setPassword(RedisPassword.of(redisProperties.getPassword()));
        sentinel.setSentinelPassword(RedisPassword.of(sentinelProperties.getPassword()));

        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(sentinel);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();

        byte[] key = "ticketing:ha:lettuce-probe".getBytes(StandardCharsets.UTF_8);
        byte[] value = "connected".getBytes(StandardCharsets.UTF_8);

        try (RedisConnection connection = connectionFactory.getConnection()) {
            assertThat(connection.ping()).isEqualTo("PONG");
            assertThat(connection.stringCommands().set(key, value)).isTrue();
            assertThat(connection.stringCommands().get(key)).isEqualTo(value);
            assertThat(connection.keyCommands().del(key)).isEqualTo(1L);
        } finally {
            connectionFactory.destroy();
        }
    }

    private void addSentinelNode(RedisSentinelConfiguration sentinel, String address) {
        int portSeparator = address.lastIndexOf(':');
        String host = address.substring(0, portSeparator);
        int port = Integer.parseInt(address.substring(portSeparator + 1));
        sentinel.sentinel(host, port);
    }

    private void verifyRedissonConnection(RedissonClient redisson) throws InterruptedException {
        RLock lock = redisson.getLock("ticketing:ha:redisson-probe");

        try {
            assertThat(lock.tryLock(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RedisProperties.class)
    @Import(RedissonSentinelConfig.class)
    static class SentinelTestConfig {
    }
}
