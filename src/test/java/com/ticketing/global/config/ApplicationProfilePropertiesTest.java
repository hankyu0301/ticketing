package com.ticketing.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationProfilePropertiesTest {

    @Test
    @DisplayName("Dev 프로필은 MaxScale와 Redis Sentinel 연결 설정을 사용한다")
    void devProfileUsesHaConnections() {
        try (ConfigurableApplicationContext context = runWithProfile("dev")) {
            DataSourceProperties dataSource = context.getBean(DataSourceProperties.class);
            RedisProperties redis = context.getBean(RedisProperties.class);

            assertThat(dataSource.getUrl())
                    .startsWith("jdbc:mariadb:sequential://localhost:14006,localhost:14007/");
            assertThat(dataSource.getUsername()).isEqualTo("ticketing");
            assertThat(redis.getSentinel()).isNotNull();
            assertThat(redis.getSentinel().getMaster()).isEqualTo("ticketing-master");
            assertThat(redis.getSentinel().getNodes())
                    .containsExactly("localhost:26379", "localhost:26380", "localhost:26381");
        }
    }

    @Test
    @DisplayName("Standalone 프로필은 단일 MariaDB와 단일 Redis 연결 설정을 사용한다")
    void standaloneProfileUsesDirectConnections() {
        try (ConfigurableApplicationContext context = runWithProfile("standalone")) {
            DataSourceProperties dataSource = context.getBean(DataSourceProperties.class);
            RedisProperties redis = context.getBean(RedisProperties.class);

            assertThat(dataSource.getUrl()).startsWith("jdbc:mariadb://localhost:3306/");
            assertThat(dataSource.getUsername()).isEqualTo("root");
            assertThat(redis.getHost()).isEqualTo("localhost");
            assertThat(redis.getPort()).isEqualTo(6379);
            assertThat(redis.getSentinel()).isNull();
        }
    }

    @Test
    @DisplayName("Loadtest 프로필은 부하테스트용 직접 연결 설정을 사용한다")
    void loadtestProfileUsesDirectConnections() {
        try (ConfigurableApplicationContext context = runWithProfile("loadtest")) {
            DataSourceProperties dataSource = context.getBean(DataSourceProperties.class);
            RedisProperties redis = context.getBean(RedisProperties.class);

            assertThat(dataSource.getUrl()).startsWith("jdbc:mariadb://localhost:3306/");
            assertThat(dataSource.getUsername()).isEqualTo("root");
            assertThat(redis.getHost()).isEqualTo("localhost");
            assertThat(redis.getPort()).isEqualTo(6379);
            assertThat(redis.getSentinel()).isNull();
        }
    }

    private ConfigurableApplicationContext runWithProfile(String profile) {
        return new SpringApplicationBuilder(ProfilePropertiesTestConfig.class)
                .profiles(profile)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.main.banner-mode=off",
                        "spring.main.log-startup-info=false"
                )
                .run();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({DataSourceProperties.class, RedisProperties.class})
    static class ProfilePropertiesTestConfig {
    }
}
