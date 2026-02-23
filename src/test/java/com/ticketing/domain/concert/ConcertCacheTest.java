package com.ticketing.domain.concert;

import com.ticketing.api.concert.dto.ConcertDto;
import com.ticketing.api.concert.dto.ConcertScheduleCreateRequest;
import com.ticketing.api.concert.dto.ConcertScheduleDto;
import com.ticketing.infrastructure.persistence.concert.ConcertMapper;
import com.ticketing.infrastructure.persistence.concert.ConcertScheduleMapper;
import com.ticketing.domain.concert.model.Concert;
import com.ticketing.domain.concert.model.ConcertSchedule;
import com.ticketing.application.concert.service.ConcertService;
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

    @Test
    @DisplayName("스케줄 생성 시 캐시 무효화: 생성 후 다시 조회하면 DB를 호출해야 한다")
    void createSchedule_cache_evict() {
        // given
        Long concertId = 1L;

        // 스케줄 생성 요청 객체 세팅
        ConcertScheduleCreateRequest request = new ConcertScheduleCreateRequest();
        ReflectionTestUtils.setField(request, "concertDate", LocalDateTime.now().plusDays(1));
        ReflectionTestUtils.setField(request, "reservationOpenAt", LocalDateTime.now());
        ReflectionTestUtils.setField(request, "totalSeats", 100);

        given(concertMapper.existsById(concertId)).willReturn(true);
        given(concertScheduleMapper.findAllByConcertId(concertId))
                .willReturn(List.of());

        // 1. 첫 번째 조회 -> DB 호출 발생 (Cache Miss)
        concertService.getSchedules(concertId);
        verify(concertScheduleMapper, times(1)).findAllByConcertId(concertId);

        // 2. 두 번째 조회 -> DB 호출 안 함 (Cache Hit)
        concertService.getSchedules(concertId);
        verify(concertScheduleMapper, times(1)).findAllByConcertId(concertId);

        // 3. 스케줄 생성 -> @CacheEvict 동작하여 schedules 키 삭제됨 (Cache Evict)
        concertService.createSchedule(concertId, request);

        // 4. 세 번째 조회 -> 캐시가 비었으므로 DB 다시 호출 (Cache Miss)
        concertService.getSchedules(concertId);

        // then: 총 호출 횟수가 2번이어야 함
        verify(concertScheduleMapper, times(2)).findAllByConcertId(concertId);
    }
}