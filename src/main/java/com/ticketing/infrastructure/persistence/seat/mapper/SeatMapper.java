package com.ticketing.infrastructure.persistence.seat.mapper;

import com.ticketing.domain.seat.model.Seat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface SeatMapper {

    void insertSeatsInBatch(List<Seat> seats);

    long countByScheduleId(Long scheduleId);

    List<Seat> findAllByScheduleId(Long scheduleId);

    Optional<Seat> findById(Long seatId);

    // 좌석 예약 생성 시 사용하는 비관적 락 조회 (SELECT ... FOR UPDATE)
    Optional<Seat> findByIdForUpdate(Long seatId);

    int reserveIfAvailable(@Param("seatId") Long seatId,
                           @Param("scheduleId") Long scheduleId,
                           @Param("memberId") Long memberId);

    void update(Seat seat);
}
