package com.ticketing.infrastructure.persistence.payment;

import com.ticketing.domain.payment.model.PaymentOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface PaymentOutboxMapper {
    void save(PaymentOutbox outbox);

    Optional<PaymentOutbox> findById(Long id);

    List<PaymentOutbox> findReadyCandidates(@Param("limit") int limit,
                                           @Param("maxAttempts") int maxAttempts);

    List<PaymentOutbox> findExpiredClaims(int limit);

    int tryClaim(@Param("id") Long id, @Param("claimToken") String claimToken,
                 @Param("leaseMicros") long leaseMicros, @Param("maxAttempts") int maxAttempts);

    int markPublished(@Param("id") Long id, @Param("claimToken") String claimToken);

    int markFailed(@Param("id") Long id, @Param("claimToken") String claimToken,
                   @Param("parked") boolean parked, @Param("delayMicros") long delayMicros,
                   @Param("error") String error, @Param("expiredOnly") boolean expiredOnly);
}
