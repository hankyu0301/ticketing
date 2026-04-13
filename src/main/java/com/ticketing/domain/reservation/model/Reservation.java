package com.ticketing.domain.reservation.model;

import com.ticketing.global.common.BaseEntity;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation extends BaseEntity {
    private Long reservationId;
    private Long memberId;
    private Long seatId;
    private Long scheduleId;

    private ReservationStatus status;

    private LocalDateTime reservedAt;

    private LocalDateTime expiredAt;

    public enum ReservationStatus {
        PENDING,    // 결제 대기 중
        CONFIRMED,  // 예약 확정 (결제 완료)
        CANCELLED   // 취소됨 (시간 초과 등)
    }

    @Builder
    public Reservation(Long memberId, Long seatId, Long scheduleId, LocalDateTime reservedAt, LocalDateTime expiredAt) {
        this.memberId = memberId;
        this.seatId = seatId;
        this.scheduleId = scheduleId;
        this.status = ReservationStatus.PENDING;
        this.reservedAt = reservedAt;
        this.expiredAt = expiredAt;
    }

    public static Reservation create(Long memberId, Long seatId, Long scheduleId, LocalDateTime reservedAt, LocalDateTime expiredAt) {
        return Reservation.builder()
                .memberId(memberId)
                .seatId(seatId)
                .scheduleId(scheduleId)
                .reservedAt(reservedAt)
                .expiredAt(expiredAt)
                .build();
    }

    public void confirm() {
        this.status = ReservationStatus.CONFIRMED;
    }

    public void cancel() {
        this.status = ReservationStatus.CANCELLED;
    }

    public boolean isExpired(LocalDateTime now) {
        return expiredAt != null && !expiredAt.isAfter(now);
    }

    public boolean canBePaidBy(Long memberId, LocalDateTime now) {
        return status == ReservationStatus.PENDING
                && Objects.equals(this.memberId, memberId)
                && !isExpired(now);
    }
}
