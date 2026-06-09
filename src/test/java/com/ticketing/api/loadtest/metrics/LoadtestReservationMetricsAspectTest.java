package com.ticketing.api.loadtest.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class LoadtestReservationMetricsAspectTest {

    private static final String RESERVATION_SERVICE_METRIC = "ticketing.loadtest.reservation.service";

    @Test
    void recordReservationService_success_recordsSuccessTimer() throws Throwable {
        // given
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        LoadtestReservationMetricsAspect aspect = new LoadtestReservationMetricsAspect(meterRegistry);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        given(joinPoint.proceed()).willReturn("ok");

        // when
        Object result = aspect.recordReservationService(joinPoint);

        // then
        Timer timer = meterRegistry.find(RESERVATION_SERVICE_METRIC)
                .tag("outcome", "success")
                .tag("exception", "none")
                .timer();

        assertThat(result).isEqualTo("ok");
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void recordReservationService_error_recordsErrorTimer() throws Throwable {
        // given
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        LoadtestReservationMetricsAspect aspect = new LoadtestReservationMetricsAspect(meterRegistry);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        given(joinPoint.proceed()).willThrow(new IllegalStateException("boom"));

        // when & then
        assertThatThrownBy(() -> aspect.recordReservationService(joinPoint))
                .isInstanceOf(IllegalStateException.class);

        Timer timer = meterRegistry.find(RESERVATION_SERVICE_METRIC)
                .tag("outcome", "error")
                .tag("exception", "IllegalStateException")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }
}
