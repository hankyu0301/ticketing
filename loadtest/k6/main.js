import * as smokeSeededLogin from './scenarios/smoke-seeded-login.js';
import * as loadQueueEnter from './scenarios/load-queue-enter.js';
import * as loadQueueRankPolling from './scenarios/load-queue-rank-polling.js';
import * as loadReservationUnique from './scenarios/load-reservation-unique.js';
import * as stressReservationContention from './scenarios/stress-reservation-contention.js';
import * as burstReservationContention from './scenarios/burst-reservation-contention.js';

const scenarios = {
    'smoke-seeded-login': smokeSeededLogin,
    'load-queue-enter': loadQueueEnter,
    'load-queue-rank-polling': loadQueueRankPolling,
    'load-reservation-unique': loadReservationUnique,
    'stress-reservation-contention': stressReservationContention,
    'burst-reservation-contention': burstReservationContention
};

const scenarioName = __ENV.SCENARIO || 'smoke-seeded-login';
const selected = scenarios[scenarioName];

if (!selected) {
    throw new Error(`Unsupported scenario: ${scenarioName}`);
}

export const options = selected.options;
export const setup = selected.setup || (() => null);
export default selected.run;
export const handleSummary = selected.handleSummary;
