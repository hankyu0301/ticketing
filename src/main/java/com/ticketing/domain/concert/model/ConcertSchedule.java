package com.ticketing.domain.concert.model;

import com.ticketing.global.common.BaseEntity;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConcertSchedule extends BaseEntity {
    private Long scheduleId;
    private Long concertId;

    // 실제 공연 날짜
    private LocalDateTime concertDate;

    // 예매 오픈 시간
    private LocalDateTime reservationOpenAt;

    private Integer totalSeats;

    @Builder
    private ConcertSchedule(Long concertId, LocalDateTime concertDate, LocalDateTime reservationOpenAt, Integer totalSeats) {
        this.concertId = concertId;
        this.concertDate = concertDate;
        this.reservationOpenAt = reservationOpenAt;
        this.totalSeats = totalSeats;
    }

    public static ConcertSchedule create(Long concertId, LocalDateTime concertDate, LocalDateTime reservationOpenAt, Integer totalSeats) {
        return ConcertSchedule.builder()
                .concertId(concertId)
                .concertDate(concertDate)
                .reservationOpenAt(reservationOpenAt)
                .totalSeats(totalSeats)
                .build();
    }
}