package com.ticketing.application.reservation.facade;

import com.ticketing.api.reservation.dto.ReservationCreateRequest;
import com.ticketing.api.reservation.dto.ReservationResponse;
import com.ticketing.application.reservation.service.DefaultReservationService;
import com.ticketing.domain.reservation.exception.ReservationTooManyRequestsException;
import com.ticketing.domain.seat.exception.SeatAlreadyReservedException;
import com.ticketing.infrastructure.lock.exception.LockAcquisitionFailureException;
import com.ticketing.infrastructure.lock.executor.LockExecutor;
import com.ticketing.infrastructure.persistence.seat.SeatUnavailableCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DistributedLockReservationFacadeTest {

    @Mock
    private LockExecutor lockExecutor;

    @Mock
    private DefaultReservationService reservationService;

    @Mock
    private SeatUnavailableCacheRepository seatUnavailableCacheRepository;

    @InjectMocks
    private DistributedLockReservationFacade distributedLockReservationFacade;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(distributedLockReservationFacade, "unavailableCacheEnabled", true);
        ReflectionTestUtils.setField(distributedLockReservationFacade, "holdDuration", Duration.ofMinutes(30));
        ReflectionTestUtils.setField(distributedLockReservationFacade, "temporaryTtlBuffer", Duration.ofMinutes(1));
    }

    /*
    * any(Supplier.class)는 제네릭 타입 정보가 없는 raw Supplier로 취급되어서
    * Supplier<ReservationResponse>로 캐스팅할 때 제네릭 타입 소거 경고
    * Supplier<ReservationResponse>라는 걸 알고 있으니, 경고를 이 메서드 안에서만 무시
    * */
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
        verify(seatUnavailableCacheRepository).isUnavailable(20L, 10L);
        verify(lockExecutor).execute(eq("seat:10"), any(Supplier.class));
        verify(reservationService).reserve(10L, memberId, 20L);
        verify(seatUnavailableCacheRepository).markTemporary(20L, 10L, Duration.ofMinutes(31));
    }

    @Test
    @DisplayName("좌석 unavailable cache에 있으면 락을 얻기 전에 이미 예약된 좌석으로 거절한다")
    void reserve_failFast_whenSeatUnavailableCacheHit() {
        Long memberId = 1L;
        ReservationCreateRequest request = request(20L, 10L);
        given(seatUnavailableCacheRepository.isUnavailable(20L, 10L)).willReturn(true);

        assertThatThrownBy(() -> distributedLockReservationFacade.reserve(memberId, request))
                .isInstanceOf(SeatAlreadyReservedException.class);

        verify(lockExecutor, never()).execute(any(), any());
        verify(reservationService, never()).reserve(any(), any(), any());
        verify(seatUnavailableCacheRepository, never()).markTemporary(any(), any(), any());
    }

    @Test
    @DisplayName("지정 시간 안에 락을 얻지 못하면 예약 과부하 예외로 변환한다")
    @SuppressWarnings("unchecked")
    void reserve_fail_whenLockAcquisitionTimeout() {
        Long memberId = 1L;
        ReservationCreateRequest request = request(20L, 10L);
        given(lockExecutor.execute(eq("seat:10"), any(Supplier.class)))
                .willThrow(new LockAcquisitionFailureException("timeout"));

        assertThatThrownBy(() -> distributedLockReservationFacade.reserve(memberId, request))
                .isInstanceOf(ReservationTooManyRequestsException.class);

        verify(seatUnavailableCacheRepository).isUnavailable(20L, 10L);
        verify(reservationService, never()).reserve(any(), any(), any());
        verify(seatUnavailableCacheRepository, never()).markTemporary(any(), any(), any());
    }

    private ReservationCreateRequest request(Long scheduleId, Long seatId) {
        ReservationCreateRequest request = new ReservationCreateRequest();
        ReflectionTestUtils.setField(request, "scheduleId", scheduleId);
        ReflectionTestUtils.setField(request, "seatId", seatId);
        return request;
    }
}
