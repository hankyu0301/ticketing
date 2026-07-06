package com.ticketing.global.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

@Profile({"standalone", "loadtest"})
@Configuration
@ConditionalOnProperty(name = "ticketing.lock.strategy", havingValue = "REDISSON")
public class RedissonSingleServerConfig {

    private static final String REDISSON_HOST_PREFIX = "redis://";

    private final RedisProperties redisProperties;

    public RedissonSingleServerConfig(RedisProperties redisProperties) {
        this.redisProperties = redisProperties;
    }

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        return Redisson.create(redissonConfiguration());
    }

    Config redissonConfiguration() {
        Config config = new Config();
        var singleServer = config.useSingleServer()
                .setAddress(toRedisAddress(redisProperties.getHost() + ":" + redisProperties.getPort()));

        if (StringUtils.hasText(redisProperties.getPassword())) {
            singleServer.setPassword(redisProperties.getPassword());
        }

        return config;
    }

    private String toRedisAddress(String address) {
        return REDISSON_HOST_PREFIX + address.trim();
    }
}
