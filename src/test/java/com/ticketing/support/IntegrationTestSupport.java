package com.ticketing.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
public abstract class IntegrationTestSupport {

    @Container
    static final MariaDBContainer<?> MARIA_DB_CONTAINER = new MariaDBContainer<>("mariadb:10.6")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MARIA_DB_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", MARIA_DB_CONTAINER::getUsername);
        registry.add("spring.datasource.password", MARIA_DB_CONTAINER::getPassword);
        registry.add("spring.datasource.driver-class-name", MARIA_DB_CONTAINER::getDriverClassName);
    }

}
