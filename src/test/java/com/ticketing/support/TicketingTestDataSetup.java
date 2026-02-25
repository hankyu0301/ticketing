package com.ticketing.support;

import com.ticketing.infrastructure.persistence.concert.ConcertMapper;
import com.ticketing.infrastructure.persistence.concert.ConcertScheduleMapper;
import com.ticketing.domain.concert.model.Concert;
import com.ticketing.domain.concert.model.ConcertSchedule;
import com.ticketing.infrastructure.persistence.member.MemberMapper;
import com.ticketing.domain.member.model.Member;
import com.ticketing.infrastructure.seat.mapper.SeatMapper;
import com.ticketing.domain.seat.model.Seat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class TicketingTestDataSetup {

    @Autowired private ConcertMapper concertMapper;
    @Autowired private ConcertScheduleMapper concertScheduleMapper;
    @Autowired private SeatMapper seatMapper;
    @Autowired private MemberMapper memberMapper;

    public record TestData(Long concertId, Long scheduleId, Long seatId) {}

    /**
     * 공연 -> 스케줄 -> 좌석 1개 생성 후 ID 반환
     */
    public TestData setupConcertAndSeat() {
        // 1. 공연 생성
        Concert concert = Concert.create("테스트 공연", "설명");
        concertMapper.insertConcert(concert);
        Long concertId = concert.getConcertId();

        // 2. 스케줄 생성
        ConcertSchedule schedule = ConcertSchedule.create(concertId, LocalDateTime.now().plusDays(1), LocalDateTime.now().minusDays(1), 100);
        concertScheduleMapper.insertSchedule(schedule);
        Long scheduleId = schedule.getScheduleId();

        // 3. 좌석 생성
        Seat seat = Seat.create(scheduleId, 1, "VIP", BigDecimal.valueOf(10000));
        seatMapper.insertSeatsInBatch(Collections.singletonList(seat));

        List<Seat> seats = seatMapper.findAllByScheduleId(scheduleId);
        Long seatId = seats.get(0).getSeatId();

        return new TestData(concertId, scheduleId, seatId);
    }

    public List<Long> setupMembers(int count) {
        List<Long> memberIds = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Member member = Member.create("user" + i + "@test.com", "password", "nickname" + i);
            memberMapper.insertMember(member);
            memberIds.add(member.getMemberId());
        }
        return memberIds;
    }
}