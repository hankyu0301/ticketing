/**
 * 커스텀 k6 metric과 시나리오 코드용 기록 helper를 선언한다.
 * 시나리오 파일은 metric tag를 직접 작성하지 않고 recorder 함수를 호출한다.
 */
import { Rate, Trend } from 'k6/metrics';

// 인증 실패와 예상하지 못한 실패를 기록하는 metric.
export const authFailureRate = new Rate('auth_failure_rate');
export const unexpectedFailureRate = new Rate('unexpected_failure_rate');

// queue enter와 rank polling 흐름을 기록하는 metric.
export const queueEnterEnteredRate = new Rate('queue_enter_entered_rate');
export const queueEnterWaitingRate = new Rate('queue_enter_waiting_rate');
export const queueRankEnteredRate = new Rate('queue_rank_entered_rate');
export const queueRetryAfterTrend = new Trend('queue_retry_after_seconds');
export const queueRankTrend = new Trend('queue_rank_value');
export const queueTimeToActiveTrend = new Trend('queue_time_to_active_ms');

// 예약 결과를 기록하는 metric.
export const reservationSuccessRate = new Rate('reservation_success_rate');
export const reservationBusinessFailureRate = new Rate('reservation_business_failure_rate');
export const reservationUnexpectedFailureRate = new Rate('reservation_unexpected_failure_rate');
export const reservationDurationTrend = new Trend('reservation_duration_ms');
export const reservationSuccessDurationTrend = new Trend('reservation_success_duration_ms');
export const reservationBusinessFailureDurationTrend = new Trend('reservation_business_failure_duration_ms');
export const reservationUnexpectedFailureDurationTrend = new Trend('reservation_unexpected_failure_duration_ms');

/**
 * 시나리오, step, HTTP status tag와 함께 예상하지 못한 실패를 기록한다.
 */
export function recordUnexpectedFailure(scenarioName, step, status) {
    unexpectedFailureRate.add(1, {
        scenario: scenarioName,
        step,
        status: String(status)
    });
}

/**
 * queue enter 응답 결과를 기록한다.
 */
export function recordQueueEnter(queueData, scenarioName) {
    const tags = { scenario: scenarioName };
    queueEnterEnteredRate.add(queueData.entered ? 1 : 0, tags);
    queueEnterWaitingRate.add(queueData.entered ? 0 : 1, tags);
    queueRetryAfterTrend.add(queueData.retryAfter || 0, tags);
    queueRankTrend.add(queueData.rank || 0, tags);
}

/**
 * 현재 queue rank polling 상태를 기록한다.
 */
export function recordQueueRank(queueData, scenarioName) {
    const tags = { scenario: scenarioName };
    queueRankEnteredRate.add(queueData.entered ? 1 : 0, tags);
    queueRetryAfterTrend.add(queueData.retryAfter || 0, tags);
    queueRankTrend.add(queueData.rank || 0, tags);
}

/**
 * waiting 사용자가 active 상태가 되기까지 걸린 시간을 기록한다.
 */
export function recordQueueTimeToActive(waitedMs, scenarioName) {
    queueTimeToActiveTrend.add(waitedMs, { scenario: scenarioName });
}

/**
 * 예약 성공, 예상 가능한 비즈니스 실패, 예상하지 못한 실패 비율을 기록한다.
 * extraTags가 있으면 좌석군 같은 시나리오별 분석 tag를 함께 기록한다.
 */
export function recordReservation(status, scenarioName, allowBusinessFailure, extraTags = {}, durationMs = null) {
    const tags = { scenario: scenarioName, status: String(status), ...extraTags };
    reservationSuccessRate.add(status === 201 ? 1 : 0, tags);
    reservationBusinessFailureRate.add(status === 400 ? 1 : 0, tags);

    const isUnexpected = allowBusinessFailure
        ? !(status === 201 || status === 400)
        : status !== 201;

    reservationUnexpectedFailureRate.add(isUnexpected ? 1 : 0, tags);

    if (durationMs === null || durationMs === undefined) {
        return;
    }

    reservationDurationTrend.add(durationMs, tags);

    if (status === 201) {
        reservationSuccessDurationTrend.add(durationMs, tags);
        return;
    }

    if (allowBusinessFailure && status === 400) {
        reservationBusinessFailureDurationTrend.add(durationMs, tags);
        return;
    }

    reservationUnexpectedFailureDurationTrend.add(durationMs, tags);
}
