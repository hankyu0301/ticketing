package com.ticketing.infrastructure.lock.repository;

import com.ticketing.infrastructure.lock.mapper.NamedLockMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NamedLockRepositoryTest {

    @Mock
    private NamedLockMapper namedLockMapper;

    @InjectMocks
    private NamedLockRepository namedLockRepository;

    @Test
    @DisplayName("NamedLock 대기 시간은 공통 waitTimeMs 설정을 초 단위로 사용한다")
    void getLock_usesConfiguredWaitTime() {
        ReflectionTestUtils.setField(namedLockRepository, "waitTimeMs", 10000L);
        given(namedLockMapper.getLock("seat:1", 10)).willReturn(1);

        namedLockRepository.getLock("seat:1");

        verify(namedLockMapper).getLock("seat:1", 10);
    }

    @Test
    @DisplayName("NamedLock 대기 시간은 1초 미만이어도 최소 1초로 보정한다")
    void getLock_usesMinimumOneSecond() {
        ReflectionTestUtils.setField(namedLockRepository, "waitTimeMs", 100L);
        given(namedLockMapper.getLock("seat:1", 1)).willReturn(1);

        namedLockRepository.getLock("seat:1");

        verify(namedLockMapper).getLock("seat:1", 1);
    }

    @Test
    @DisplayName("NamedLock 획득 결과가 1이 아니면 예외를 던진다")
    void getLock_fail_whenMapperReturnsNotOne() {
        ReflectionTestUtils.setField(namedLockRepository, "waitTimeMs", 1000L);
        given(namedLockMapper.getLock("seat:1", 1)).willReturn(0);

        assertThatThrownBy(() -> namedLockRepository.getLock("seat:1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NamedLock 락 획득 실패");
    }
}
