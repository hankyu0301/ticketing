import exec from 'k6/execution';
import {
    constantArrivalOptions,
    getTargets,
    pickUserByVuId
} from '../lib/config.js';
import { createReservation, getLoadtestSession } from '../lib/auth.js';
import { createSummaryHandler } from '../lib/summary.js';
import { expectStatus, parseResponseMessage } from '../lib/checks.js';
import { recordReservation, recordUnexpectedFailure } from '../lib/metrics.js';

const scenarioName = 'stress-reservation-contention';
const targets = getTargets();
const contentionUserRange = targets.reservation.contentionUsers;
const contentionZones = normalizeContentionZones(targets.reservation);
const scheduleId = targets.concert.scheduleId;
const totalZoneWeight = contentionZones.reduce((sum, zone) => sum + zone.weight, 0);

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

/**
 * targets JSON의 인기 좌석군 설정을 검증하고, 구버전 contentionSeats 설정도 지원한다.
 */
function normalizeContentionZones(reservationTargets) {
    const zones = reservationTargets.contentionZones;
    if (Array.isArray(zones) && zones.length > 0) {
        return zones.map((zone, index) => normalizeZone(zone, `reservation.contentionZones[${index}]`));
    }

    const fallback = reservationTargets.contentionSeats;
    if (!fallback) {
        throw new Error('reservation must define contentionZones or contentionSeats');
    }

    return [normalizeZone({
        name: 'contention',
        startSeatId: fallback.startSeatId,
        count: fallback.count,
        weight: 1
    }, 'reservation.contentionSeats')];
}

/**
 * 하나의 좌석군 설정이 재현 가능한 경합 테스트에 필요한 값을 갖는지 확인한다.
 */
function normalizeZone(zone, label) {
    if (!zone || !zone.name || zone.startSeatId === undefined || zone.count === undefined || zone.weight === undefined) {
        throw new Error(`${label} must define name, startSeatId, count, and weight`);
    }

    if (zone.startSeatId <= 0 || zone.count <= 0 || zone.weight <= 0) {
        throw new Error(`${label} must define positive startSeatId, count, and weight`);
    }

    return {
        name: String(zone.name),
        startSeatId: zone.startSeatId,
        count: zone.count,
        weight: zone.weight
    };
}

/**
 * iteration index를 기준으로 좌석군 weight를 반영해 인기 좌석군을 선택한다.
 * 랜덤을 쓰지 않아 같은 설정이면 같은 요청 분포가 재현된다.
 */
function pickZoneByIndex(index) {
    const bucket = index % totalZoneWeight;
    let cumulative = 0;

    for (const zone of contentionZones) {
        const next = cumulative + zone.weight;
        if (bucket < next) {
            return {
                zone,
                offsetInZone: bucket - cumulative
            };
        }
        cumulative = next;
    }

    throw new Error(`Failed to pick contention zone for index ${index}`);
}

/**
 * 선택된 좌석군 안에서 요청을 좌석에 분산한다.
 * 인기 구역일수록 더 많은 요청이 같은 좌석군 안에서 반복되어 자연스럽게 경합이 커진다.
 */
function pickSeatInZone(index, zone, offsetInZone) {
    const cycle = Math.floor(index / totalZoneWeight);
    const seatOffset = ((cycle * zone.weight) + offsetInZone) % zone.count;
    return zone.startSeatId + seatOffset;
}

export function run() {
    const index = exec.scenario.iterationInTest;
    const user = pickUserByVuId(contentionUserRange, exec.vu.idInTest, 'reservation.contentionUsers');
    const picked = pickZoneByIndex(index);
    const seatId = pickSeatInZone(index, picked.zone, picked.offsetInZone);
    const authSession = getLoadtestSession(user, scenarioName);
    const metricTags = { seat_zone: picked.zone.name };
    const response = createReservation(scheduleId, seatId, authSession, scenarioName, metricTags);

    recordReservation(response.status, scenarioName, true, metricTags);

    if (!expectStatus(response, [201, 400], 'reservation-contention')) {
        recordUnexpectedFailure(scenarioName, 'reservation-create', response.status);
        throw new Error(`Unexpected contention response for seat ${seatId}: ${parseResponseMessage(response)}`);
    }
}

export const handleSummary = createSummaryHandler(scenarioName);
