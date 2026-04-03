package com.ticketing.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

@Configuration
public class RedisScriptConfig {

    @Bean
    public RedisScript<Long> queueEnterScript() {
        return RedisScript.of(new ClassPathResource("scripts/queue_enter.lua"), Long.class);
    }

    @Bean
    public RedisScript<Long> queueStatusScript() {
        return RedisScript.of(new ClassPathResource("scripts/queue_status.lua"), Long.class);
    }

    @Bean
    public RedisScript<List> activeMemberScript() {
        return RedisScript.of(new ClassPathResource("scripts/active_member.lua"), List.class);
    }

    @Bean
    public RedisScript<Long> validExtensionScript() {
        return RedisScript.of(new ClassPathResource("scripts/queue_valid_extension.lua"), Long.class);
    }

    @Bean
    public RedisScript<Long> cleanupProcessingScheduleScript() {
        return RedisScript.of(new ClassPathResource("scripts/queue_cleanup_processing_schedule.lua"), Long.class);
    }
}
