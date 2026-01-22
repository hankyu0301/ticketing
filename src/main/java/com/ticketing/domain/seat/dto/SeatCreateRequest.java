package com.ticketing.domain.seat.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

import static com.ticketing.global.common.exception.seat.SeatErrorMessage.*;

@Getter
@NoArgsConstructor
public class SeatCreateRequest {

    @NotNull(message = TOTAL_SEATS_MUST_NOT_EMPTY)
    @Min(value = 1, message = TOTAL_SEATS_MUST_BE_POSITIVE)
    private Integer totalSeats;

    @NotBlank(message = SEAT_GRADE_MUST_NOT_EMPTY)
    private String grade;

    @NotNull(message = SEAT_PRICE_MUST_NOT_EMPTY)
    @PositiveOrZero(message = SEAT_PRICE_MUST_BE_POSITIVE_OR_ZERO)
    private BigDecimal price;
}