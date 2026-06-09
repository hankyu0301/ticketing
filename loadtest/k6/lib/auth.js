/**
 * 인증, loadtest 세션, 대기열, 좌석, 예약 API helper를 제공한다.
 * 예약 시나리오는 setup()에서 세션을 미리 만들고 측정 요청에서는 명시적 쿠키를 재사용한다.
 */
import http from 'k6/http';
import {
    csrfHeaderParams,
    formParams,
    getRequest,
    jsonParams,
    postRequest,
    deleteRequest
} from './http-client.js';
import { expectStatus, parseResponseData, parseResponseMessage } from './checks.js';
import { authFailureRate } from './metrics.js';

let cachedSession;
let cachedLoadtestSession;

/**
 * k6 HTTP 응답에서 쿠키 이름으로 쿠키 값을 읽는다.
 */
function cookieValue(response, name) {
    const responseCookie = response.cookies?.[name];
    if (responseCookie && responseCookie.length > 0) {
        return responseCookie[0].value;
    }

    return null;
}

/**
 * 필수 쿠키를 읽고, 없으면 step 이름을 포함해 즉시 실패시킨다.
 */
function requireCookie(response, name, step) {
    const value = cookieValue(response, name);
    if (!value) {
        throw new Error(`${step} did not return ${name} cookie`);
    }
    return value;
}

/**
 * 준비된 세션에 명시적 쿠키가 있으면 요청 params에 Cookie 헤더를 추가한다.
 */
function withSessionCookies(params, authSession) {
    if (!authSession.cookieHeader) {
        return params;
    }

    return {
        ...params,
        headers: {
            ...params.headers,
            Cookie: authSession.cookieHeader
        }
    };
}

/**
 * 현재 k6 cookie jar 기준으로 CSRF token을 발급받는다.
 */
export function fetchCsrfToken() {
    const response = getRequest('/api/v1/auth/csrf', {
        tags: { scenario: 'auth', step: 'csrf-bootstrap', endpoint: 'auth-csrf' }
    });

    if (!expectStatus(response, [200], 'csrf-bootstrap')) {
        authFailureRate.add(1, { scenario: 'auth-bootstrap', status: String(response.status) });
        throw new Error(`CSRF bootstrap failed: ${parseResponseMessage(response)}`);
    }

    const data = response.json('data');
    if (!data || !data.token) {
        throw new Error('CSRF bootstrap response does not contain token');
    }

    return data.token;
}

/**
 * 실제 회원 로그인 endpoint를 호출하고 세션 객체를 반환한다.
 */
export function loginAs(user, scenarioName) {
    const loginCsrfToken = fetchCsrfToken();
    const response = postRequest(
        '/api/v1/members/login',
        {
            email: user.email,
            password: user.password
        },
        formParams(loginCsrfToken, { scenario: scenarioName, step: 'login', endpoint: 'member-login' })
    );

    if (!expectStatus(response, [200], 'login')) {
        authFailureRate.add(1, { scenario: scenarioName, status: String(response.status) });
        throw new Error(`Login failed for ${user.email}: ${parseResponseMessage(response)}`);
    }

    return {
        csrfToken: fetchCsrfToken(),
        user
    };
}

/**
 * 요청 사용자가 바뀌지 않았다면 VU 내부에서 실제 로그인 세션을 재사용한다.
 */
export function getAuthenticatedSession(user, scenarioName) {
    if (!cachedSession || cachedSession.user.email !== user.email) {
        cachedSession = loginAs(user, scenarioName);
    }
    return cachedSession;
}

/**
 * BCrypt 로그인 비용 없이 loadtest 전용 Spring Security 세션을 만든다.
 */
export function createLoadtestSession(user, scenarioName) {
    const response = postRequest(
        '/api/v1/loadtest/sessions',
        JSON.stringify({ email: user.email }),
        {
            tags: { scenario: scenarioName, step: 'loadtest-session', endpoint: 'loadtest-session' },
            headers: {
                'Content-Type': 'application/json'
            }
        }
    );

    if (!expectStatus(response, [200], 'loadtest-session')) {
        authFailureRate.add(1, { scenario: scenarioName, status: String(response.status) });
        throw new Error(`Loadtest session failed for ${user.email}: ${parseResponseMessage(response)}`);
    }

    const sessionUser = parseResponseData(response);
    return {
        csrfToken: fetchCsrfToken(),
        user: {
            ...user,
            memberId: sessionUser.memberId
        }
    };
}

/**
 * 요청 사용자가 바뀌지 않았다면 VU 내부에서 loadtest 전용 세션을 재사용한다.
 */
export function getLoadtestSession(user, scenarioName) {
    if (!cachedLoadtestSession || cachedLoadtestSession.user.email !== user.email) {
        cachedLoadtestSession = createLoadtestSession(user, scenarioName);
    }
    return cachedLoadtestSession;
}

/**
 * setup()에서 사용할 세션을 만들고, 측정 요청에 넘길 명시적 쿠키를 반환한다.
 */
export function createPreparedLoadtestSession(user, scenarioName) {
    const jar = new http.CookieJar();
    const response = postRequest(
        '/api/v1/loadtest/sessions',
        JSON.stringify({ email: user.email }),
        {
            jar,
            tags: { scenario: scenarioName, step: 'setup-loadtest-session', endpoint: 'setup-loadtest-session' },
            headers: {
                'Content-Type': 'application/json'
            }
        }
    );

    if (!expectStatus(response, [200], 'setup-loadtest-session')) {
        authFailureRate.add(1, { scenario: scenarioName, status: String(response.status), step: 'setup-loadtest-session' });
        throw new Error(`Prepared loadtest session failed for ${user.email}: ${parseResponseMessage(response)}`);
    }

    const sessionCookie = requireCookie(response, 'SESSION', 'setup-loadtest-session');
    const csrfResponse = getRequest('/api/v1/auth/csrf', {
        jar,
        tags: { scenario: scenarioName, step: 'setup-csrf-bootstrap', endpoint: 'setup-auth-csrf' }
    });

    if (!expectStatus(csrfResponse, [200], 'setup-csrf-bootstrap')) {
        authFailureRate.add(1, { scenario: scenarioName, status: String(csrfResponse.status), step: 'setup-csrf-bootstrap' });
        throw new Error(`Prepared CSRF bootstrap failed for ${user.email}: ${parseResponseMessage(csrfResponse)}`);
    }

    const csrfData = csrfResponse.json('data');
    if (!csrfData || !csrfData.token) {
        throw new Error(`Prepared CSRF bootstrap response does not contain token for ${user.email}`);
    }

    const sessionUser = parseResponseData(response);
    const xsrfCookie = cookieValue(csrfResponse, 'XSRF-TOKEN') || csrfData.token;

    return {
        csrfToken: csrfData.token,
        cookieHeader: `SESSION=${sessionCookie}; XSRF-TOKEN=${xsrfCookie}`,
        user: {
            ...user,
            memberId: sessionUser.memberId
        }
    };
}

/**
 * CSRF와 준비된 세션 쿠키로 queue enter endpoint를 호출한다.
 */
export function postQueueEnter(scheduleId, authSession, scenarioName) {
    return postRequest(
        `/api/v1/queue/enter?scheduleId=${scheduleId}`,
        null,
        withSessionCookies(csrfHeaderParams(authSession.csrfToken, { scenario: scenarioName, endpoint: 'queue-enter' }), authSession)
    );
}

/**
 * 현재 세션 기준으로 queue rank endpoint를 호출한다.
 */
export function getQueueRank(scheduleId, scenarioName) {
    return getRequest(`/api/v1/queue/rank?scheduleId=${scheduleId}`, {
        tags: { scenario: scenarioName, endpoint: 'queue-rank' }
    });
}

/**
 * active 사용자용 schedule 좌석 조회 endpoint를 호출한다.
 */
export function lookupSeats(scheduleId, scenarioName) {
    return getRequest(`/api/v1/schedules/${scheduleId}/seats`, {
        tags: { scenario: scenarioName, endpoint: 'seat-lookup' }
    });
}

/**
 * 하나의 schedule과 seat에 대해 예약 생성 endpoint를 호출한다.
 */
export function createReservation(scheduleId, seatId, authSession, scenarioName) {
    return postRequest(
        '/api/v1/reservations',
        JSON.stringify({ scheduleId, seatId }),
        withSessionCookies(jsonParams(authSession.csrfToken, { scenario: scenarioName, endpoint: 'reservation-create' }), authSession)
    );
}

/**
 * queue active slot을 반납하기 위해 active leave endpoint를 호출한다.
 */
export function leaveActive(scheduleId, authSession, scenarioName) {
    return deleteRequest(
        `/api/v1/queue/active/${scheduleId}`,
        null,
        withSessionCookies(csrfHeaderParams(authSession.csrfToken, { scenario: scenarioName, endpoint: 'active-leave' }), authSession)
    );
}
