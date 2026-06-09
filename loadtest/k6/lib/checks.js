/**
 * k6 check helper와 응답 파싱 helper를 모아둔다.
 * 시나리오 파일은 이 모듈을 통해 실패 메시지와 JSON 접근 방식을 일관되게 유지한다.
 */
import { check } from 'k6';

/**
 * 응답 status가 허용된 status 목록에 포함되는지 k6 check로 기록한다.
 */
export function expectStatus(response, allowedStatuses, label) {
    return check(response, {
        [`${label} status`]: (res) => allowedStatuses.includes(res.status)
    });
}

/**
 * 응답 body를 JSON으로 파싱하고, 실패하면 명확한 오류를 던진다.
 */
export function parseJson(response) {
    try {
        return response.json();
    } catch (error) {
        throw new Error(`Invalid JSON response: ${error.message}`);
    }
}

/**
 * 공통 ResponseDTO JSON 응답에서 data 필드를 반환한다.
 */
export function parseResponseData(response) {
    return parseJson(response).data;
}

/**
 * JSON 응답에서 서비스 오류 메시지를 추출하고, 실패하면 raw body를 반환한다.
 */
export function parseResponseMessage(response) {
    try {
        const parsed = parseJson(response);
        return parsed.message || response.body;
    } catch (error) {
        return response.body;
    }
}
