/**
 * k6 HTTP 호출에 프로젝트 base URL과 공통 요청 헤더 규칙을 적용한다.
 * 시나리오 파일은 k6/http를 직접 호출하지 않고 이 모듈을 통해 요청한다.
 */
import http from 'k6/http';
import { getBaseUrl } from './config.js';

/**
 * 절대 URL 또는 상대 API path를 실제 요청 URL로 변환한다.
 */
export function buildUrl(path) {
    if (path.startsWith('http://') || path.startsWith('https://')) {
        return path;
    }

    return `${getBaseUrl()}${path}`;
}

/**
 * 설정된 loadtest base URL로 GET 요청을 보낸다.
 */
export function getRequest(path, params = {}) {
    return http.get(buildUrl(path), params);
}

/**
 * 설정된 loadtest base URL로 POST 요청을 보낸다.
 */
export function postRequest(path, body, params = {}) {
    return http.post(buildUrl(path), body, params);
}

/**
 * 설정된 loadtest base URL로 DELETE 요청을 보낸다.
 */
export function deleteRequest(path, body, params = {}) {
    return http.del(buildUrl(path), body, params);
}

/**
 * JSON 요청에 필요한 CSRF 헤더와 metric tag를 만든다.
 */
export function jsonParams(csrfToken, tags) {
    return {
        tags,
        headers: {
            'Content-Type': 'application/json',
            'X-XSRF-TOKEN': csrfToken
        }
    };
}

/**
 * form 요청에 필요한 CSRF 헤더와 metric tag를 만든다.
 */
export function formParams(csrfToken, tags) {
    return {
        tags,
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
            'X-XSRF-TOKEN': csrfToken
        }
    };
}

/**
 * Content-Type이 필요 없는 CSRF 보호 요청의 공통 params를 만든다.
 */
export function csrfHeaderParams(csrfToken, tags) {
    return {
        tags,
        headers: {
            'X-XSRF-TOKEN': csrfToken
        }
    };
}
