import exec from 'k6/execution';
import {
    getRangeCount,
    getScenarioProfile,
    getTargets,
    perVuIterationsOptions,
    pickNumericIdCircular,
    pickUserByVuId
} from '../lib/config.js';
import { createPreparedLoadtestSession, createReservation } from '../lib/auth.js';
import { createSummaryHandler } from '../lib/summary.js';
import { expectStatus, parseResponseMessage } from '../lib/checks.js';
import { recordReservation, recordUnexpectedFailure } from '../lib/metrics.js';

const scenarioName = 'burst-reservation-contention';
const targets = getTargets();
const burstUserRange = targets.reservation.burstUsers || targets.reservation.contentionUsers;
const burstSeatRange = targets.reservation.burstSeats || targets.reservation.contentionSeats;
const scheduleId = targets.concert.scheduleId;
const safeVus = getRangeCount(burstUserRange, 'reservation.burstUsers');
const safeSeats = getRangeCount(burstSeatRange, 'reservation.burstSeats');
const scenarioDefaults = {
    vus: Math.min(20, safeVus),
    iterations: 1,
    maxDuration: '1m'
};
const profile = getScenarioProfile(scenarioName, scenarioDefaults);

export const options = perVuIterationsOptions(
    scenarioName,
    'burstReservationContention',
    scenarioDefaults,
    {
        reservation_unexpected_failure_rate: ['rate==0']
    }
);

export function setup() {
    if (profile.vus > safeVus) {
        throw new Error(`Configured VUs ${profile.vus} exceeds reservation.burstUsers count ${safeVus}`);
    }

    const sessionsByVuId = {};
    for (let vuId = 1; vuId <= profile.vus; vuId += 1) {
        const user = pickUserByVuId(burstUserRange, vuId, 'reservation.burstUsers');
        sessionsByVuId[String(vuId)] = createPreparedLoadtestSession(user, scenarioName);
    }

    return { sessionsByVuId };
}

export function run(data) {
    const index = exec.scenario.iterationInTest;
    const seatId = pickNumericIdCircular(burstSeatRange, index, 'reservation.burstSeats');
    const authSession = data?.sessionsByVuId?.[String(exec.vu.idInTest)];

    if (!authSession) {
        throw new Error(`Prepared session not found for VU ${exec.vu.idInTest}`);
    }

    const metricTags = {
        seat_pool_size: String(safeSeats)
    };
    const response = createReservation(scheduleId, seatId, authSession, scenarioName, metricTags);

    recordReservation(response.status, scenarioName, true, metricTags, response.timings.duration);

    if (!expectStatus(response, [201, 400, 429], 'burst-reservation-contention')) {
        recordUnexpectedFailure(scenarioName, 'reservation-create', response.status);
        throw new Error(`Unexpected burst contention response for seat ${seatId}: ${parseResponseMessage(response)}`);
    }
}

export const handleSummary = createSummaryHandler(scenarioName);
