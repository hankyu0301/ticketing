package com.ticketing.domain.concert.service;

import com.ticketing.domain.concert.dto.ConcertCreateRequest;
import com.ticketing.domain.concert.dto.ConcertDto;
import com.ticketing.domain.concert.dto.ConcertScheduleCreateRequest;
import com.ticketing.domain.concert.dto.ConcertScheduleDto;
import com.ticketing.domain.concert.mapper.ConcertMapper;
import com.ticketing.domain.concert.mapper.ConcertScheduleMapper;
import com.ticketing.domain.concert.model.Concert;
import com.ticketing.domain.concert.model.ConcertSchedule;
import com.ticketing.global.common.exception.concert.ConcertErrorMessage;
import com.ticketing.global.common.exception.concert.ConcertNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConcertService {

    private final ConcertMapper concertMapper;
    private final ConcertScheduleMapper concertScheduleMapper;

    // 콘서트 생성
    @Transactional
    public Long createConcert(ConcertCreateRequest request) {
        Concert concert = request.toEntity();
        concertMapper.insertConcert(concert);
        return concert.getConcertId();
    }

    // 스케줄 생성
    @Transactional
    @CacheEvict(value = "schedules", key = "#concertId")
    public Long createSchedule(Long concertId, ConcertScheduleCreateRequest request) {
        // 콘서트 존재 여부 확인
        if (!concertMapper.existsById(concertId)) {
            throw new ConcertNotFoundException(ConcertErrorMessage.NOT_EXIST_CONCERT);
        }

        ConcertSchedule schedule = request.toEntity(concertId);
        concertScheduleMapper.insertSchedule(schedule);
        return schedule.getScheduleId();
    }

    // 콘서트 조회
    @Transactional(readOnly = true)
    @Cacheable(value = "concerts", key = "#concertId", cacheManager = "redisCacheManager")
    public ConcertDto getConcert(Long concertId) {
        Concert concert =  concertMapper.findById(concertId)
                .orElseThrow(() -> new ConcertNotFoundException(ConcertErrorMessage.NOT_EXIST_CONCERT));
        return new ConcertDto(concert);
    }

    // 스케줄 목록 조회
    @Transactional(readOnly = true)
    @Cacheable(value = "schedules", key = "#concertId", cacheManager = "redisCacheManager")
    public List<ConcertScheduleDto> getSchedules(Long concertId) {
        return concertScheduleMapper.findAllByConcertId(concertId)
                .stream().map(ConcertScheduleDto::new).collect(Collectors.toList());
    }
}