package com.ticketing.domain.reservation.model;

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
public class Reservation extends BaseEntity {
    private Long reservationId;
    private Long memberId;
    private Long seatId;
    private Long scheduleId;

    private ReservationStatus status;

    private LocalDateTime reservedAt;

    // 예약 생성 시점 + 5분 등으로 설정될 만료 시간
    private LocalDateTime expiredAt;

    public enum ReservationStatus {
        PENDING,    // 결제 대기 중
        CONFIRMED,  // 예약 확정 (결제 완료)
        CANCELLED   // 취소됨 (시간 초과 등)
    }
}