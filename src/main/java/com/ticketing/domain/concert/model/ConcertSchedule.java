package com.ticketing.domain.concert.model;

import com.ticketing.global.common.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConcertSchedule extends BaseEntity {
    private Long scheduleId;
    private Long concertId;

    // 실제 공연 날짜
    private LocalDateTime concertDate;

    // 예매 오픈 시간
    private LocalDateTime reservationOpenAt;

    private Integer totalSeats;
}