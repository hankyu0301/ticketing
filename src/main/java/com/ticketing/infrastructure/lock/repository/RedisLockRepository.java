package com.ticketing.infrastructure.lock.repository;

import org.springframework.data.redis.connection.ReturnType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RedisLockRepository {

    private final RedisTemplate<String, String> redisTemplate;

    public Boolean lock(String key, String token, Duration ttl) {
        return redisTemplate
                .opsForValue()
                .setIfAbsent(generateKey(key), token, ttl);
    }

    public boolean renew(String key, String token, Duration ttl) {
        Long result = redisTemplate.execute(
                connection -> {
                    byte[] rawKey = redisTemplate.getStringSerializer().serialize(generateKey(key));
                    byte[] rawToken = redisTemplate.getStringSerializer().serialize(token);
                    byte[] rawTtl = String.valueOf(ttl.toMillis()).getBytes(StandardCharsets.UTF_8);

                    return (Long) connection.scriptingCommands().eval(
                            RENEW_SCRIPT.getBytes(StandardCharsets.UTF_8),
                            ReturnType.INTEGER,
                            1,
                            rawKey,
                            rawToken,
                            rawTtl
                    );
                },
                true
        );

        return isSuccess(result);
    }

    public boolean unlock(String key, String token) {
        Long result = redisTemplate.execute(
                connection -> {
                    byte[] rawKey = redisTemplate.getStringSerializer().serialize(generateKey(key));
                    byte[] rawToken = redisTemplate.getStringSerializer().serialize(token);

                    return (Long) connection.scriptingCommands().eval(
                            UNLOCK_SCRIPT.getBytes(StandardCharsets.UTF_8),
                            ReturnType.INTEGER,
                            1,
                            rawKey,
                            rawToken
                    );
                },
                true
        );

        return isSuccess(result);
    }

    private String generateKey(String key) {
        return "lock:" + key;
    }

    private boolean isSuccess(Long result) {
        return result != null && result == 1L;
    }

    private static final String UNLOCK_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """;

    private static final String RENEW_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('pexpire', KEYS[1], ARGV[2])
            end
            return 0
            """;
}
