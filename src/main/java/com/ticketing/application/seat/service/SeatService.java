package com.ticketing.application.seat.service;

import com.ticketing.api.seat.dto.SeatCreateRequest;
import com.ticketing.api.seat.dto.SeatResponse;
import com.ticketing.infrastructure.persistence.seat.mapper.SeatMapper;
import com.ticketing.domain.seat.model.Seat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class SeatService {

    private final SeatMapper seatMapper;

    @Transactional
    public void createSeats(Long scheduleId, SeatCreateRequest request) {
        List<Seat> seats = IntStream.rangeClosed(1, request.getTotalSeats())
                .mapToObj(i -> Seat.create(
                        scheduleId,
                        i,
                        request.getGrade(),
                        request.getPrice()
                ))
                .collect(Collectors.toList());

        seatMapper.insertSeatsInBatch(seats);
    }

    @Transactional(readOnly = true)
    public List<SeatResponse> getSeats(Long scheduleId) {
        return seatMapper.findAllByScheduleId(scheduleId).stream()
                .map(SeatResponse::new)
                .collect(Collectors.toList());
    }
}