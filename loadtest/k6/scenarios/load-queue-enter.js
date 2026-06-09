import exec from 'k6/execution';
import { constantArrivalOptions, getTargets, pickUserCircular } from '../lib/config.js';
import { getLoadtestSession, postQueueEnter } from '../lib/auth.js';
import { createSummaryHandler } from '../lib/summary.js';
import { expectStatus, parseResponseData, parseResponseMessage } from '../lib/checks.js';
import { recordQueueEnter, recordUnexpectedFailure } from '../lib/metrics.js';

const scenarioName = 'load-queue-enter';
const targets = getTargets();
const enterUserRange = targets.queue.enterUsers;
const scheduleId = targets.concert.scheduleId;

export const options = constantArrivalOptions(
    scenarioName,
    'loadQueueEnter',
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
    const user = pickUserCircular(enterUserRange, exec.scenario.iterationInTest, 'queue.enterUsers');
    const authSession = getLoadtestSession(user, scenarioName);
    const response = postQueueEnter(scheduleId, authSession, scenarioName);

    if (!expectStatus(response, [200], 'queue-enter')) {
        recordUnexpectedFailure(scenarioName, 'queue-enter', response.status);
        throw new Error(`Queue enter failed for ${user.email}: ${parseResponseMessage(response)}`);
    }

    recordQueueEnter(parseResponseData(response), scenarioName);
}

export const handleSummary = createSummaryHandler(scenarioName);
