import exec from 'k6/execution';
import { getRangeCount, getTargets, perVuIterationsOptions, pickUserByVuId } from '../lib/config.js';
import { loginAs } from '../lib/auth.js';
import { createSummaryHandler } from '../lib/summary.js';

const scenarioName = 'smoke-seeded-login';
const targets = getTargets();
const authUserRange = targets.auth.users;
const authUserCount = getRangeCount(authUserRange, 'auth.users');

export const options = perVuIterationsOptions(
    scenarioName,
    'smokeSeededLogin',
    {
        vus: Math.min(5, authUserCount),
        iterations: 1,
        maxDuration: '1m'
    },
    {
        http_req_failed: ['rate<0.01'],
        auth_failure_rate: ['rate==0']
    }
);

export function run() {
    const user = pickUserByVuId(authUserRange, exec.vu.idInTest, 'auth.users');
    loginAs(user, scenarioName);
}

export const handleSummary = createSummaryHandler(scenarioName);
