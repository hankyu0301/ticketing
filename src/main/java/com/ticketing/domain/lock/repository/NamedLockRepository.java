package com.ticketing.domain.lock.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;

@Slf4j
@Repository
@ConditionalOnProperty(name = "ticketing.lock.strategy", havingValue = "NAMED")
public class NamedLockRepository {

    private final JdbcTemplate jdbcTemplate;

    public NamedLockRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public void getLock(String lockName) {
        String sql = "SELECT GET_LOCK(?, 3)"; // 3초 대기
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class, lockName);
        checkResult(result, "NamedLock 획득 실패: " + lockName);
    }

    public void releaseLock(String lockName) {
        String sql = "SELECT RELEASE_LOCK(?)";
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class, lockName);
        checkResult(result, "NamedLock 해제 실패: " + lockName);
    }

    private void checkResult(Integer result, String message) {
        // 1(성공)이 아니면 에러 처리
        if (result == null || result != 1) {
            throw new IllegalStateException(message);
        }
    }
}