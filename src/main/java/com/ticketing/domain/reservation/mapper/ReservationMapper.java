package com.ticketing.domain.reservation.mapper;

import com.ticketing.domain.reservation.model.Reservation;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ReservationMapper {
    void save(Reservation reservation);
    Reservation findById(Long reservationId);
    List<Reservation> findAllByMemberId(Long memberId);
}