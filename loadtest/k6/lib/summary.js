/**
 * 사람이 읽기 쉬운 결과 파일과 JSON 결과 파일을 함께 생성하는 k6 handleSummary 함수를 만든다.
 * 콘솔 출력은 짧게 유지하고, 전체 k6 데이터는 이후 분석을 위해 보존한다.
 */
import { getStringEnv } from './config.js';

/**
 * 하나의 k6 metric을 짧은 한 줄 요약 문자열로 변환한다.
 */
function metricLine(data, metricName, label, missingValue = 'n/a', preferCount = false) {
    const metric = data.metrics[metricName];
    if (!metric || !metric.values) {
        return `${label}: ${missingValue}`;
    }

    const values = metric.values;
    const avg = values.avg !== undefined ? values.avg.toFixed(2) : null;
    const p95 = values['p(95)'] !== undefined ? values['p(95)'].toFixed(2) : null;
    const p99 = values['p(99)'] !== undefined ? values['p(99)'].toFixed(2) : null;
    const rate = values.rate !== undefined ? values.rate.toFixed(4) : null;
    const count = values.count !== undefined ? values.count : null;

    if (preferCount && count !== null) {
        return `${label}: count=${count}`;
    }

    if (rate !== null) {
        return `${label}: rate=${rate}`;
    }

    if (count !== null && avg === null && p95 === null && p99 === null) {
        return `${label}: count=${count}`;
    }

    return `${label}: avg=${avg ?? 'n/a'}, p95=${p95 ?? 'n/a'}, p99=${p99 ?? 'n/a'}`;
}

/**
 * 주어진 시나리오 이름에 맞는 k6 handleSummary callback을 만든다.
 */
export function createSummaryHandler(scenarioName) {
    return function handleSummary(data) {
        const baseName = getStringEnv('RESULT_BASENAME', `loadtest/k6/results/${scenarioName}`);
        const text = [
            `Scenario: ${scenarioName}`,
            metricLine(data, 'iterations', 'iterations'),
            metricLine(data, 'dropped_iterations', 'dropped_iterations', 'count=0', true),
            metricLine(data, 'http_reqs', 'http_reqs'),
            metricLine(data, 'http_req_duration', 'http_req_duration'),
            metricLine(data, 'http_req_duration{endpoint:reservation-create}', 'reservation_http_req_duration'),
            metricLine(data, 'http_req_failed', 'http_req_failed'),
            metricLine(data, 'auth_failure_rate', 'auth_failure_rate'),
            metricLine(data, 'queue_enter_entered_rate', 'queue_enter_entered_rate'),
            metricLine(data, 'queue_enter_waiting_rate', 'queue_enter_waiting_rate'),
            metricLine(data, 'queue_rank_entered_rate', 'queue_rank_entered_rate'),
            metricLine(data, 'queue_retry_after_seconds', 'queue_retry_after_seconds'),
            metricLine(data, 'queue_rank_value', 'queue_rank_value'),
            metricLine(data, 'queue_time_to_active_ms', 'queue_time_to_active_ms'),
            metricLine(data, 'reservation_success_rate', 'reservation_success_rate'),
            metricLine(data, 'reservation_business_failure_rate', 'reservation_business_failure_rate'),
            metricLine(data, 'reservation_lock_timeout_rate', 'reservation_lock_timeout_rate'),
            metricLine(data, 'reservation_unexpected_failure_rate', 'reservation_unexpected_failure_rate'),
            metricLine(data, 'reservation_duration_ms', 'reservation_duration_ms'),
            metricLine(data, 'reservation_success_duration_ms', 'reservation_success_duration_ms'),
            metricLine(data, 'reservation_business_failure_duration_ms', 'reservation_business_failure_duration_ms'),
            metricLine(data, 'reservation_lock_timeout_duration_ms', 'reservation_lock_timeout_duration_ms'),
            metricLine(data, 'reservation_unexpected_failure_duration_ms', 'reservation_unexpected_failure_duration_ms'),
            metricLine(data, 'unexpected_failure_rate', 'unexpected_failure_rate')
        ].join('\n');

        return {
            stdout: `${text}\n`,
            [`${baseName}-summary.json`]: JSON.stringify(data, null, 2),
            [`${baseName}-summary.txt`]: `${text}\n`
        };
    };
}
