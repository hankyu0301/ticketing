package com.ticketing.infrastructure.persistence.payment;

import com.ticketing.domain.payment.model.Payment;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;

@Mapper
public interface PaymentMapper {

    void save(Payment payment);

    Optional<Payment> findById(Long paymentId);

    Optional<Payment> findByIdForUpdate(Long paymentId);

    Optional<Payment> findByReservationId(Long reservationId);

    void update(Payment payment);
}
