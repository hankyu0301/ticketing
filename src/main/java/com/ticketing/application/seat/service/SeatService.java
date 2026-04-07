package com.ticketing.application.seat.service;

import com.ticketing.api.seat.dto.SeatCreateRequest;
import com.ticketing.api.seat.dto.SeatResponse;
import com.ticketing.domain.concert.exception.ConcertErrorMessage;
import com.ticketing.domain.concert.exception.ConcertScheduleNotFoundException;
import com.ticketing.domain.concert.model.ConcertSchedule;
import com.ticketing.domain.seat.exception.SeatBadRequestException;
import com.ticketing.domain.seat.model.Seat;
import com.ticketing.infrastructure.persistence.concert.ConcertScheduleMapper;
import com.ticketing.infrastructure.persistence.seat.mapper.SeatMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.ticketing.domain.seat.exception.SeatErrorMessage.SEAT_COUNT_MISMATCH;
import static com.ticketing.domain.seat.exception.SeatErrorMessage.SEATS_ALREADY_CREATED;

@Service
@RequiredArgsConstructor
public class SeatService {

    private final ConcertScheduleMapper concertScheduleMapper;
    private final SeatMapper seatMapper;

    @Transactional
    public void createSeats(Long scheduleId, SeatCreateRequest request) {
        ConcertSchedule schedule = concertScheduleMapper.findById(scheduleId)
                .orElseThrow(() -> new ConcertScheduleNotFoundException(ConcertErrorMessage.NOT_EXIST_SCHEDULE));

        if (!schedule.getTotalSeats().equals(request.getTotalSeats())) {
            throw new SeatBadRequestException(SEAT_COUNT_MISMATCH);
        }

        if (seatMapper.countByScheduleId(scheduleId) > 0) {
            throw new SeatBadRequestException(SEATS_ALREADY_CREATED);
        }

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
