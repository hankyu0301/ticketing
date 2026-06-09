/**
 * queue 및 E2E 시나리오에서 사용하는 대기열 polling helper를 제공한다.
 * retryAfter/fixed-delay polling 세부사항은 숨기고 queue rank metric을 기록한다.
 */
import { sleep } from 'k6';
import { getIntEnv, getStringEnv } from './config.js';
import { getQueueRank } from './auth.js';
import { expectStatus, parseResponseData, parseResponseMessage } from './checks.js';
import { recordQueueRank, recordQueueTimeToActive, recordUnexpectedFailure } from './metrics.js';

/**
 * retryAfter 또는 fixed override 기준으로 다음 polling 대기 시간을 선택한다.
 */
function getPollDelaySeconds(queueData) {
    const mode = getStringEnv('POLL_MODE', 'retryAfter');
    if (mode === 'fixed') {
        return getIntEnv('FIXED_POLL_SECONDS', 1);
    }
    return Math.max(queueData.retryAfter || 1, 1);
}

/**
 * queue rank 응답을 한 번 조회하고 rank 관련 metric을 기록한다.
 */
export function readQueueRank(scheduleId, scenarioName) {
    const response = getQueueRank(scheduleId, scenarioName);

    if (!expectStatus(response, [200], 'queue-rank')) {
        recordUnexpectedFailure(scenarioName, 'queue-rank', response.status);
        throw new Error(`Queue rank failed: ${parseResponseMessage(response)}`);
    }

    const queueData = parseResponseData(response);
    recordQueueRank(queueData, scenarioName);
    return queueData;
}

/**
 * 사용자가 active 상태가 되거나 최대 polling 횟수를 초과할 때까지 queue rank를 조회한다.
 */
export function waitUntilEntered(scheduleId, scenarioName) {
    const maxPolls = getIntEnv('MAX_POLLS', 30);
    const startedAt = Date.now();

    for (let index = 0; index < maxPolls; index += 1) {
        const queueData = readQueueRank(scheduleId, scenarioName);

        if (queueData.entered) {
            recordQueueTimeToActive(Date.now() - startedAt, scenarioName);
            return queueData;
        }

        sleep(getPollDelaySeconds(queueData));
    }

    throw new Error(`Queue polling exceeded max polls for schedule ${scheduleId}`);
}
