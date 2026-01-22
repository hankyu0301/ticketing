package com.ticketing.domain.seat.dto;

import com.ticketing.domain.seat.model.Seat;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class SeatResponse {
    private Long seatId;
    private Integer seatNo;
    private String grade;
    private BigDecimal price;
    private Seat.SeatStatus status;

    public SeatResponse(Seat seat) {
        this.seatId = seat.getSeatId();
        this.seatNo = seat.getSeatNo();
        this.grade = seat.getGrade();
        this.price = seat.getPrice();
        this.status = seat.getStatus();
    }
}