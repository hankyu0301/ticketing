package com.ticketing.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

@Configuration
@RequiredArgsConstructor
@EnableRedisHttpSession
public class SessionConfig {

    private final ObjectMapper objectMapper;

    /**
     * LettuceConnectionFactory는 yml 설정에 의해 자동 주입됨
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration();
        redisStandaloneConfiguration.setHostName(redisHost);
        redisStandaloneConfiguration.setPort(Integer.parseInt(redisPort));
        return new LettuceConnectionFactory(redisStandaloneConfiguration);
    }
    */

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(springSessionDefaultRedisSerializer(objectMapper));
        return redisTemplate;
    }

    @Bean
    public RedisSerializer<Object> springSessionDefaultRedisSerializer(ObjectMapper objectMapper) {
        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }
}
