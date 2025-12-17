package com.ticketing.domain.concert.mapper;

import com.ticketing.domain.concert.model.ConcertSchedule;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ConcertScheduleMapper {
    // 스케줄 생성
    void insertSchedule(ConcertSchedule schedule);

    // 특정 콘서트의 모든 스케줄 조회 (예매 날짜 선택용)
    List<ConcertSchedule> findAllByConcertId(Long concertId);

    // 특정 스케줄 단건 조회 (좌석 생성 및 예약 시 필요)
    Optional<ConcertSchedule> findById(Long scheduleId);
}