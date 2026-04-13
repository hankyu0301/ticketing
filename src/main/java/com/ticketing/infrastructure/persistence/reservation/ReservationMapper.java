package com.ticketing.infrastructure.persistence.reservation;

import com.ticketing.domain.reservation.model.Reservation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReservationMapper {
    void save(Reservation reservation);
    Reservation findById(Long reservationId);
    Reservation findByIdForUpdate(Long reservationId);
    List<Reservation> findAllByMemberId(Long memberId);
    List<Long> findExpiredPendingReservationIds(@Param("now") LocalDateTime now, @Param("limit") int limit);
    void update(Reservation reservation);
}
