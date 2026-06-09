import exec from 'k6/execution';
import { constantArrivalOptions, getTargets, pickUserCircular } from '../lib/config.js';
import { getLoadtestSession } from '../lib/auth.js';
import { readQueueRank, waitUntilEntered } from '../lib/queue.js';
import { createSummaryHandler } from '../lib/summary.js';

const scenarioName = 'load-queue-rank-polling';
const targets = getTargets();
const rankPollingUserRange = targets.queue.rankPollingUsers;
const scheduleId = targets.concert.scheduleId;

export const options = constantArrivalOptions(
    scenarioName,
    'loadQueueRankPolling',
    {
        rate: 10,
        duration: '30s',
        preAllocatedVUs: 20,
        maxVUs: 50
    },
    {
        http_req_failed: ['rate<0.05'],
        unexpected_failure_rate: ['rate==0']
    }
);

export function run() {
    const user = pickUserCircular(rankPollingUserRange, exec.scenario.iterationInTest, 'queue.rankPollingUsers');
    getLoadtestSession(user, scenarioName);

    if (__ENV.POLL_UNTIL_ACTIVE === 'true') {
        waitUntilEntered(scheduleId, scenarioName);
        return;
    }

    readQueueRank(scheduleId, scenarioName);
}

export const handleSummary = createSummaryHandler(scenarioName);
