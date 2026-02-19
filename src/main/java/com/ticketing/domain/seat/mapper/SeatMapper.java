package com.ticketing.domain.seat.mapper;

import com.ticketing.domain.seat.model.Seat;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

@Mapper
public interface SeatMapper {

    void insertSeatsInBatch(List<Seat> seats);

    List<Seat> findAllByScheduleId(Long scheduleId);

    Optional<Seat> findById(Long seatId);

    // (비관적 락) 예약 시 사용 (SELECT ... FOR UPDATE)
    Optional<Seat> findByIdWithPessimisticLock(Long seatId);

    void update(Seat seat);
}