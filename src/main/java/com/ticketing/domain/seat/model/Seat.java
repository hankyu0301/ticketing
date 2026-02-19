package com.ticketing.domain.seat.model;

import com.ticketing.global.common.BaseEntity;
import com.ticketing.global.common.exception.seat.SeatAlreadyReservedException;
import com.ticketing.global.common.exception.seat.SeatErrorMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Seat extends BaseEntity {
    private Long seatId;
    private Long scheduleId;
    private Integer seatNo;
    private String grade;
    private BigDecimal price;
    private Long memberId;
    private SeatStatus status;

    public enum SeatStatus {
        AVAILABLE, TEMPORARY, SOLD_OUT
    }

    @Builder
    private Seat(Long scheduleId, Integer seatNo, String grade, BigDecimal price, SeatStatus status) {
        this.scheduleId = scheduleId;
        this.seatNo = seatNo;
        this.grade = grade;
        this.price = price;
        this.status = status;
    }

    public static Seat create(Long scheduleId, Integer seatNo, String grade, BigDecimal price) {
        return Seat.builder()
                .scheduleId(scheduleId)
                .seatNo(seatNo)
                .grade(grade)
                .price(price)
                .status(SeatStatus.AVAILABLE)
                .build();
    }

    public void reserve(Long memberId) {
        if (this.status != SeatStatus.AVAILABLE) {
            throw new SeatAlreadyReservedException(SeatErrorMessage.ALREADY_RESERVED);
        }
        this.status = SeatStatus.TEMPORARY;
        this.memberId = memberId;
    }
}