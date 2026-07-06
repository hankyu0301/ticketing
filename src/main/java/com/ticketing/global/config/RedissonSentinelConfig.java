package com.ticketing.global.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.ReadMode;
import org.redisson.config.SentinelServersConfig;
import org.redisson.config.SubscriptionMode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

@Profile("dev")
@Configuration
@ConditionalOnProperty(name = "ticketing.lock.strategy", havingValue = "REDISSON")
public class RedissonSentinelConfig {

    private static final String REDISSON_HOST_PREFIX = "redis://";

    private final RedisProperties redisProperties;

    public RedissonSentinelConfig(RedisProperties redisProperties) {
        this.redisProperties = redisProperties;
    }

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        return Redisson.create(redissonConfiguration());
    }

    Config redissonConfiguration() {
        RedisProperties.Sentinel sentinel = redisProperties.getSentinel();
        if (sentinel == null || !StringUtils.hasText(sentinel.getMaster())) {
            throw new IllegalStateException("Redis Sentinel master must not be empty.");
        }
        if (sentinel.getNodes() == null) {
            throw new IllegalStateException("Redis Sentinel nodes must not be empty.");
        }

        String[] sentinelAddresses = sentinel.getNodes().stream()
                .filter(StringUtils::hasText)
                .map(this::toRedisAddress)
                .toArray(String[]::new);

        if (sentinelAddresses.length == 0) {
            throw new IllegalStateException("Redis Sentinel nodes must not be empty.");
        }

        Config config = new Config();
        SentinelServersConfig sentinelServers = config.useSentinelServers()
                .setMasterName(sentinel.getMaster())
                .addSentinelAddress(sentinelAddresses)
                .setReadMode(ReadMode.MASTER)
                .setSubscriptionMode(SubscriptionMode.MASTER);

        if (StringUtils.hasText(redisProperties.getPassword())) {
            sentinelServers.setPassword(redisProperties.getPassword());
        }
        if (StringUtils.hasText(sentinel.getPassword())) {
            sentinelServers.setSentinelPassword(sentinel.getPassword());
        }

        return config;
    }

    private String toRedisAddress(String address) {
        String trimmedAddress = address.trim();
        return trimmedAddress.startsWith(REDISSON_HOST_PREFIX)
                ? trimmedAddress
                : REDISSON_HOST_PREFIX + trimmedAddress;
    }
}
