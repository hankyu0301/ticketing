package com.ticketing.domain.concert;

import com.ticketing.domain.concert.dto.ConcertDto;
import com.ticketing.domain.concert.dto.ConcertScheduleDto;
import com.ticketing.domain.concert.mapper.ConcertMapper;
import com.ticketing.domain.concert.mapper.ConcertScheduleMapper;
import com.ticketing.domain.concert.model.Concert;
import com.ticketing.domain.concert.model.ConcertSchedule;
import com.ticketing.domain.concert.service.ConcertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
class ConcertCacheTest {

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:alpine"))
            .withExposedPorts(6379);

    @Autowired
    private ConcertService concertService;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private ConcertMapper concertMapper;

    @MockitoBean
    private ConcertScheduleMapper concertScheduleMapper;

    @BeforeEach
    void setUp() {
        Objects.requireNonNull(cacheManager.getCache("concerts")).clear();
        Objects.requireNonNull(cacheManager.getCache("schedules")).clear();
    }

    @Test
    @DisplayName("콘서트 조회 캐싱: 첫 조회는 DB(Mapper) 호출, 두 번째는 캐시(Redis) 조회")
    void getConcert_cache_hit() {
        // given
        Long concertId = 1L;
        Concert concert = Concert.create("아이유 콘서트", "상암");
        ReflectionTestUtils.setField(concert, "concertId", concertId);

        given(concertMapper.findById(concertId)).willReturn(Optional.of(concert));

        // 1. 첫 번째 호출 (Cache Miss)
        ConcertDto firstResult = concertService.getConcert(concertId);
        assertThat(firstResult.getTitle()).isEqualTo("아이유 콘서트");
        verify(concertMapper, times(1)).findById(concertId);


        // 2. 두 번째 호출 (Cache Hit)
        ConcertDto secondResult = concertService.getConcert(concertId);
        assertThat(secondResult.getTitle()).isEqualTo("아이유 콘서트");
        verify(concertMapper, times(1)).findById(concertId);
    }

    @Test
    @DisplayName("스케줄 목록 캐싱: 첫 조회는 DB 호출, 두 번째는 캐시 조회")
    void getSchedules_cache_hit() {
        // given
        Long concertId = 1L;
        ConcertSchedule schedule = ConcertSchedule.create(concertId, LocalDateTime.now(), LocalDateTime.now(), 50);
        ReflectionTestUtils.setField(schedule, "scheduleId", 100L);

        given(concertScheduleMapper.findAllByConcertId(concertId))
                .willReturn(List.of(schedule));

        // 1. 첫 번째 호출 (Cache Miss)
        List<ConcertScheduleDto> firstResult = concertService.getSchedules(concertId);
        assertThat(firstResult).hasSize(1);
        verify(concertScheduleMapper, times(1)).findAllByConcertId(concertId);

        // 2. 두 번째 호출 (Cache Hit)
        List<ConcertScheduleDto> secondResult = concertService.getSchedules(concertId);
        assertThat(secondResult).hasSize(1);
        verify(concertScheduleMapper, times(1)).findAllByConcertId(concertId);
    }
}