package com.ticketing.application.queue.service;

import com.ticketing.domain.concert.model.ConcertSchedule;
import com.ticketing.infrastructure.persistence.concert.ConcertScheduleMapper;
import com.ticketing.infrastructure.persistence.queue.QueueRedisRepository;
import com.ticketing.infrastructure.persistence.queue.QueueScheduleMeta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QueueScheduleMetaLoaderTest {

    @InjectMocks
    private QueueScheduleMetaLoader queueScheduleMetaLoader;

    @Mock
    private QueueRedisRepository queueRedisRepository;

    @Mock
    private ConcertScheduleMapper concertScheduleMapper;

    @Test
    @DisplayName("cache hit이면 Redis meta를 그대로 반환한다")
    void getOrLoadReturnsCachedMeta() {
        QueueScheduleMeta cachedMeta = new QueueScheduleMeta(1L, 100L, 200L);
        given(queueRedisRepository.findScheduleMeta(1L)).willReturn(Optional.of(cachedMeta));

        Optional<QueueScheduleMeta> result = queueScheduleMetaLoader.getOrLoad(1L);

        assertThat(result).contains(cachedMeta);
        verify(concertScheduleMapper, never()).findById(anyLong());
    }

    @Test
    @DisplayName("cache miss이면 DB에서 조회하고 Redis에 적재한다")
    void getOrLoadCachesDbResultOnMiss() {
        ConcertSchedule schedule = ConcertSchedule.create(
                1L,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusHours(1),
                100
        );
        ReflectionTestUtils.setField(schedule, "scheduleId", 11L);

        given(queueRedisRepository.findScheduleMeta(11L)).willReturn(Optional.empty());
        given(concertScheduleMapper.findById(11L)).willReturn(Optional.of(schedule));

        Optional<QueueScheduleMeta> result = queueScheduleMetaLoader.getOrLoad(11L);

        assertThat(result).isPresent();
        verify(queueRedisRepository).saveScheduleMeta(
                eq(11L),
                eq(result.orElseThrow().reservationOpenAtMs()),
                eq(result.orElseThrow().concertDateMs())
        );
    }
}
