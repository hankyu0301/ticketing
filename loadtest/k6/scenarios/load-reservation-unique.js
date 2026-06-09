import exec from 'k6/execution';
import {
    constantArrivalOptions,
    getBooleanEnv,
    getRangeCount,
    getScenarioProfile,
    getStringEnv,
    getTargets,
    pickNumericIdByIndex,
    pickUserByVuId,
    sharedIterationsOptions
} from '../lib/config.js';
import { createPreparedLoadtestSession, createReservation, leaveActive } from '../lib/auth.js';
import { createSummaryHandler } from '../lib/summary.js';
import { expectStatus, parseResponseMessage } from '../lib/checks.js';
import { recordReservation, recordUnexpectedFailure } from '../lib/metrics.js';

const scenarioName = 'load-reservation-unique';
const targets = getTargets();
const activeUserRange = targets.reservation.activeUsers;
const uniqueSeatRange = targets.reservation.uniqueSeats;
const scheduleId = targets.concert.scheduleId;
const safeVus = getRangeCount(activeUserRange, 'reservation.activeUsers');
const safeIterations = getRangeCount(uniqueSeatRange, 'reservation.uniqueSeats');
const executor = getStringEnv('RESERVATION_EXECUTOR', 'shared-iterations');
const scenarioDefaults = {
    vus: Math.min(20, safeVus),
    iterations: Math.min(20, safeIterations),
    maxDuration: '2m',
    rate: 20,
    duration: '30s',
    preAllocatedVUs: Math.min(20, safeVus),
    maxVUs: Math.min(50, safeVus)
};
const profile = getScenarioProfile(scenarioName, scenarioDefaults);
const thresholds = {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{endpoint:reservation-create}': ['p(95)<5000'],
    reservation_unexpected_failure_rate: ['rate==0']
};

if (!['shared-iterations', 'constant-arrival-rate'].includes(executor)) {
    throw new Error(`Unsupported reservation executor: ${executor}`);
}

export const options = executor === 'constant-arrival-rate'
    ? constantArrivalOptions(scenarioName, 'loadReservationUnique', scenarioDefaults, thresholds)
    : sharedIterationsOptions(scenarioName, 'loadReservationUnique', scenarioDefaults, thresholds);

function preparedSessionCount() {
    if (executor === 'constant-arrival-rate') {
        return profile.maxVUs;
    }
    return profile.vus;
}

export function setup() {
    const sessions = preparedSessionCount();
    if (sessions > safeVus) {
        throw new Error(`Configured VUs ${sessions} exceeds reservation.activeUsers count ${safeVus}`);
    }

    const sessionsByVuId = {};
    for (let vuId = 1; vuId <= sessions; vuId += 1) {
        const user = pickUserByVuId(activeUserRange, vuId, 'reservation.activeUsers');
        sessionsByVuId[String(vuId)] = createPreparedLoadtestSession(user, scenarioName);
    }

    return { sessionsByVuId };
}

export function run(data) {
    const index = exec.scenario.iterationInTest;
    if (index >= safeIterations) {
        throw new Error(`Unique reservation scenario exceeded configured safe iterations: ${safeIterations}`);
    }

    const seatId = pickNumericIdByIndex(uniqueSeatRange, index, 'reservation.uniqueSeats');
    const authSession = data?.sessionsByVuId?.[String(exec.vu.idInTest)];
    if (!authSession) {
        throw new Error(`Prepared session not found for VU ${exec.vu.idInTest}`);
    }

    const response = createReservation(scheduleId, seatId, authSession, scenarioName);

    recordReservation(response.status, scenarioName, false);

    if (!expectStatus(response, [201], 'reservation-create')) {
        recordUnexpectedFailure(scenarioName, 'reservation-create', response.status);
        throw new Error(`Reservation failed for seat ${seatId}: ${parseResponseMessage(response)}`);
    }

    if (getBooleanEnv('ENABLE_ACTIVE_LEAVE', false)) {
        leaveActive(scheduleId, authSession, scenarioName);
    }
}

export const handleSummary = createSummaryHandler(scenarioName);
