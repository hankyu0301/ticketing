import exec from 'k6/execution';
import {
    constantArrivalOptions,
    getTargets,
    pickNumericIdCircular,
    pickUserByVuId
} from '../lib/config.js';
import { createReservation, getLoadtestSession } from '../lib/auth.js';
import { createSummaryHandler } from '../lib/summary.js';
import { expectStatus, parseResponseMessage } from '../lib/checks.js';
import { recordReservation, recordUnexpectedFailure } from '../lib/metrics.js';

const scenarioName = 'stress-reservation-contention';
const targets = getTargets();
const contentionUserRange = targets.reservation.contentionUsers;
const contentionSeatRange = targets.reservation.contentionSeats;
const scheduleId = targets.concert.scheduleId;

export const options = constantArrivalOptions(
    scenarioName,
    'stressReservationContention',
    {
        rate: 10,
        duration: '30s',
        preAllocatedVUs: 20,
        maxVUs: 50
    },
    {
        reservation_unexpected_failure_rate: ['rate==0']
    }
);

export function run() {
    const index = exec.scenario.iterationInTest;
    const user = pickUserByVuId(contentionUserRange, exec.vu.idInTest, 'reservation.contentionUsers');
    const seatId = pickNumericIdCircular(contentionSeatRange, index, 'reservation.contentionSeats');
    const authSession = getLoadtestSession(user, scenarioName);
    const response = createReservation(scheduleId, seatId, authSession, scenarioName);

    recordReservation(response.status, scenarioName, true);

    if (!expectStatus(response, [201, 400], 'reservation-contention')) {
        recordUnexpectedFailure(scenarioName, 'reservation-create', response.status);
        throw new Error(`Unexpected contention response for seat ${seatId}: ${parseResponseMessage(response)}`);
    }
}

export const handleSummary = createSummaryHandler(scenarioName);
