package com.ticketing.domain.seat.model;

import com.ticketing.global.common.BaseEntity;
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

    // 낙관적 락
    private Long version;

    public enum SeatStatus {
        AVAILABLE, TEMPORARY, SOLD_OUT
    }
}