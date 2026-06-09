package com.ticketing.api.loadtest.controller;

import com.ticketing.global.common.response.ResponseDTO;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Profile("loadtest")
@RestController
@RequestMapping("/api/v1/loadtest/metrics")
@RequiredArgsConstructor
public class LoadtestMetricsController {

    private static final String LOADTEST_METRIC_PREFIX = "ticketing.loadtest.";

    private final MeterRegistry meterRegistry;

    @GetMapping("/timers")
    public ResponseEntity<ResponseDTO<List<TimerMetricResponse>>> getTimers() {
        List<TimerMetricResponse> timers = meterRegistry.getMeters().stream()
                .filter(meter -> meter instanceof Timer)
                .map(Meter::getId)
                .filter(id -> id.getName().startsWith(LOADTEST_METRIC_PREFIX))
                .sorted(Comparator.comparing(Meter.Id::getName).thenComparing(id -> id.getTags().toString()))
                .map(id -> toResponse(meterRegistry.get(id.getName()).tags(id.getTags()).timer()))
                .toList();

        return ResponseEntity.ok(ResponseDTO.getSuccessResult(timers));
    }

    private TimerMetricResponse toResponse(Timer timer) {
        HistogramSnapshot snapshot = timer.takeSnapshot();
        return new TimerMetricResponse(
                timer.getId().getName(),
                tags(timer.getId()),
                timer.count(),
                round(timer.totalTime(TimeUnit.MILLISECONDS)),
                round(timer.mean(TimeUnit.MILLISECONDS)),
                round(timer.max(TimeUnit.MILLISECONDS)),
                percentiles(snapshot)
        );
    }

    private Map<String, String> tags(Meter.Id id) {
        Map<String, String> tags = new LinkedHashMap<>();
        for (Tag tag : id.getTags()) {
            tags.put(tag.getKey(), tag.getValue());
        }
        return tags;
    }

    private Map<String, Double> percentiles(HistogramSnapshot snapshot) {
        Map<String, Double> percentiles = new LinkedHashMap<>();
        for (ValueAtPercentile valueAtPercentile : snapshot.percentileValues()) {
            percentiles.put(label(valueAtPercentile.percentile()), round(valueAtPercentile.value(TimeUnit.MILLISECONDS)));
        }
        return percentiles;
    }

    private String label(double percentile) {
        return "p" + Math.round(percentile * 100);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record TimerMetricResponse(
            String name,
            Map<String, String> tags,
            long count,
            double totalTimeMs,
            double meanMs,
            double maxMs,
            Map<String, Double> percentiles
    ) {
    }
}
