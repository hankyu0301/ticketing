package com.ticketing.application.reservation.facade;

import com.ticketing.api.reservation.dto.ReservationCreateRequest;
import com.ticketing.api.reservation.dto.ReservationResponse;
import com.ticketing.application.reservation.service.DefaultReservationService;
import com.ticketing.infrastructure.lock.executor.LockExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DistributedLockReservationFacadeTest {

    @Mock
    private LockExecutor lockExecutor;

    @Mock
    private DefaultReservationService reservationService;

    @InjectMocks
    private DistributedLockReservationFacade distributedLockReservationFacade;

    @Test
    @DisplayName("분산 락 예약 facade는 좌석 기반 락 이름으로 예약 서비스를 실행한다")
    @SuppressWarnings("unchecked")
    void reserve_executesReservationInsideSeatLock() {
        Long memberId = 1L;
        ReservationCreateRequest request = new ReservationCreateRequest();
        ReflectionTestUtils.setField(request, "seatId", 10L);
        ReflectionTestUtils.setField(request, "scheduleId", 20L);
        ReservationResponse expected = org.mockito.Mockito.mock(ReservationResponse.class);

        given(lockExecutor.execute(eq("seat:10"), any(Supplier.class)))
                .willAnswer(invocation -> ((Supplier<ReservationResponse>) invocation.getArgument(1)).get());
        given(reservationService.reserve(10L, memberId, 20L)).willReturn(expected);

        ReservationResponse response = distributedLockReservationFacade.reserve(memberId, request);

        assertThat(response).isSameAs(expected);
        verify(lockExecutor).execute(eq("seat:10"), any(Supplier.class));
        verify(reservationService).reserve(10L, memberId, 20L);
    }
}
