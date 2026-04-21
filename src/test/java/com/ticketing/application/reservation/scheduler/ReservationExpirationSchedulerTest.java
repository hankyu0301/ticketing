package com.ticketing.application.reservation.scheduler;

import com.ticketing.application.reservation.service.ReservationExpirationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class ReservationExpirationSchedulerTest {

    @Mock
    private ReservationExpirationService reservationExpirationService;

    @InjectMocks
    private ReservationExpirationScheduler reservationExpirationScheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reservationExpirationScheduler, "processLimit", 300);
    }

    @Test
    @DisplayName("만료 대상 예약이 없으면 추가 처리 없이 종료한다")
    void expirePendingReservationsStopsWhenNoExpiredReservationsExist() {
        given(reservationExpirationService.findExpiredPendingReservationIds(any(LocalDateTime.class), eq(300)))
                .willReturn(List.of());

        reservationExpirationScheduler.expirePendingReservations();

        verify(reservationExpirationService).findExpiredPendingReservationIds(any(LocalDateTime.class), eq(300));
        verify(reservationExpirationService, never()).expirePendingReservation(anyLong());
    }

    @Test
    @DisplayName("조회한 만료 예약 id를 순차적으로 처리한다")
    void expirePendingReservationsProcessesAllFetchedIdsSequentially() {
        given(reservationExpirationService.findExpiredPendingReservationIds(any(LocalDateTime.class), eq(300)))
                .willReturn(List.of(1L, 2L, 3L));

        reservationExpirationScheduler.expirePendingReservations();

        InOrder inOrder = inOrder(reservationExpirationService);
        inOrder.verify(reservationExpirationService).findExpiredPendingReservationIds(any(LocalDateTime.class), eq(300));
        inOrder.verify(reservationExpirationService).expirePendingReservation(1L);
        inOrder.verify(reservationExpirationService).expirePendingReservation(2L);
        inOrder.verify(reservationExpirationService).expirePendingReservation(3L);
    }

    @Test
    @DisplayName("하나의 예약 처리에 실패해도 다음 예약 처리는 계속한다")
    void expirePendingReservationsContinuesAfterSingleReservationFailure() {
        given(reservationExpirationService.findExpiredPendingReservationIds(any(LocalDateTime.class), eq(300)))
                .willReturn(List.of(1L, 2L));
        doThrow(new RuntimeException("boom"))
                .when(reservationExpirationService)
                .expirePendingReservation(1L);

        reservationExpirationScheduler.expirePendingReservations();

        verify(reservationExpirationService).findExpiredPendingReservationIds(any(LocalDateTime.class), eq(300));
        verify(reservationExpirationService).expirePendingReservation(1L);
        verify(reservationExpirationService).expirePendingReservation(2L);
        verifyNoMoreInteractions(reservationExpirationService);
    }
}
