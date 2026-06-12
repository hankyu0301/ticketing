package com.ticketing.api.loadtest.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Aspect
@Component
@Profile("loadtest")
@RequiredArgsConstructor
public class LoadtestReservationMetricsAspect {

    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<MeterKey, Timer> timers = new ConcurrentHashMap<>();

    @Around("execution(* com.ticketing.infrastructure.persistence.queue.QueueRedisRepository.validateAndExtendActiveMember(..))")
    public Object recordActiveTokenValidation(ProceedingJoinPoint joinPoint) throws Throwable {
        return record(joinPoint, "ticketing.loadtest.queue.active.validation");
    }

    @Around("execution(* com.ticketing.application.reservation.facade.*Facade.reserve(..))")
    public Object recordReservationFacade(ProceedingJoinPoint joinPoint) throws Throwable {
        return record(joinPoint, "ticketing.loadtest.reservation.facade");
    }

    @Around("execution(* com.ticketing.infrastructure.lock.executor.*LockExecutor.execute(..))")
    public Object recordLockExecutor(ProceedingJoinPoint joinPoint) throws Throwable {
        return record(joinPoint, "ticketing.loadtest.reservation.lock");
    }

    @Around("execution(* com.ticketing.application.reservation.service.DefaultReservationService.reserve(..)) || " +
            "execution(* com.ticketing.application.reservation.service.PessimisticLockReservationService.reserve(..))")
    public Object recordReservationService(ProceedingJoinPoint joinPoint) throws Throwable {
        return record(joinPoint, "ticketing.loadtest.reservation.service");
    }

    @Around("execution(* com.ticketing.infrastructure.persistence.seat.mapper.SeatMapper.findById(..))")
    public Object recordSeatFindById(ProceedingJoinPoint joinPoint) throws Throwable {
        return record(joinPoint, "ticketing.loadtest.db.seat.find");
    }

    @Around("execution(* com.ticketing.infrastructure.persistence.seat.mapper.SeatMapper.findByIdForUpdate(..))")
    public Object recordSeatFindByIdForUpdate(ProceedingJoinPoint joinPoint) throws Throwable {
        return record(joinPoint, "ticketing.loadtest.db.seat.find_for_update");
    }

    @Around("execution(* com.ticketing.infrastructure.persistence.seat.mapper.SeatMapper.update(..))")
    public Object recordSeatUpdate(ProceedingJoinPoint joinPoint) throws Throwable {
        return record(joinPoint, "ticketing.loadtest.db.seat.update");
    }

    @Around("execution(* com.ticketing.infrastructure.persistence.reservation.ReservationMapper.save(..))")
    public Object recordReservationInsert(ProceedingJoinPoint joinPoint) throws Throwable {
        return record(joinPoint, "ticketing.loadtest.db.reservation.insert");
    }

    private Object record(ProceedingJoinPoint joinPoint, String metricName) throws Throwable {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            Object result = joinPoint.proceed();
            stop(sample, metricName, "success", "none");
            return result;
        } catch (Throwable throwable) {
            stop(sample, metricName, "error", throwable.getClass().getSimpleName());
            throw throwable;
        }
    }

    private void stop(Timer.Sample sample, String metricName, String outcome, String exception) {
        sample.stop(timer(metricName, outcome, exception));
    }

    private Timer timer(String metricName, String outcome, String exception) {
        MeterKey key = new MeterKey(metricName, outcome, exception);
        return timers.computeIfAbsent(key, this::newTimer);
    }

    private Timer newTimer(MeterKey key) {
        return Timer.builder(key.metricName())
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .tag("outcome", key.outcome())
                .tag("exception", key.exception())
                .register(meterRegistry);
    }

    private record MeterKey(String metricName, String outcome, String exception) {
    }
}
