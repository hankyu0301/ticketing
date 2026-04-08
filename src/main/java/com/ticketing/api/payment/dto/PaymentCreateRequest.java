package com.ticketing.api.payment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static com.ticketing.domain.payment.exception.PaymentErrorMessage.RESERVATION_ID_MUST_NOT_EMPTY;

@Getter
@NoArgsConstructor
public class PaymentCreateRequest {

    @NotNull(message = RESERVATION_ID_MUST_NOT_EMPTY)
    private Long reservationId;
}
