/**
 * k6 target 데이터와 시나리오 profile을 로드한다.
 * 대량 데이터를 init 단계 배열로 만들지 않도록 range 기반 사용자/id 선택 helper도 제공한다.
 */
const DEFAULT_TARGETS_FILE = import.meta.resolve('../data/targets.smoke.json');

let cachedTargets;

/**
 * 기본값, 절대 경로, URL, repository 상대 경로 입력을 targets JSON 경로로 해석한다.
 */
function resolveJsonPath(path) {
    if (!path) {
        return DEFAULT_TARGETS_FILE;
    }

    if (path.includes('://') || /^[A-Za-z]:[\\/]/.test(path) || path.startsWith('/')) {
        return path;
    }

    return import.meta.resolve(`../../../${path.replace(/\\/g, '/')}`);
}

/**
 * k6 open()으로 JSON 파일을 읽고 파싱한다.
 */
function loadJson(path) {
    return JSON.parse(open(resolveJsonPath(path)));
}

/**
 * loadtest 이메일과 nickname 생성을 위해 printf 형식 또는 {value} 형식을 적용한다.
 */
function formatPattern(pattern, value) {
    const token = pattern.match(/%(0?(\d+))?d/);
    if (!token) {
        return pattern.replace('{value}', String(value));
    }

    const width = token[2] ? parseInt(token[2], 10) : 0;
    const formattedValue = width > 0
        ? String(value).padStart(width, '0')
        : String(value);

    return pattern.replace(token[0], formattedValue);
}

/**
 * range 객체에서 지원되는 시작 필드 값을 반환한다.
 */
function rangeStart(range) {
    if (range.start !== undefined) {
        return range.start;
    }
    if (range.startMemberId !== undefined) {
        return range.startMemberId;
    }
    if (range.startSeatId !== undefined) {
        return range.startSeatId;
    }
    throw new Error('Range must define start, startMemberId, or startSeatId');
}

/**
 * range 객체를 검증하고 정규화된 start/count 쌍을 반환한다.
 */
function requireRange(range, label) {
    if (!range || range.count === undefined) {
        throw new Error(`${label} must define count`);
    }
    const start = rangeStart(range);
    if (start <= 0 || range.count <= 0) {
        throw new Error(`${label} must define positive start and count`);
    }
    return { start, count: range.count };
}

/**
 * 현재 k6 프로세스에서 사용할 targets JSON을 로드하고 캐시한다.
 */
export function getTargets() {
    if (!cachedTargets) {
        cachedTargets = loadJson(__ENV.TARGETS_FILE);
    }
    return cachedTargets;
}

/**
 * 환경변수, targets JSON, localhost 기본값 순서로 API base URL을 결정한다.
 */
export function getBaseUrl() {
    const baseUrl = __ENV.BASE_URL || getTargets().baseUrl || 'http://localhost:8080';
    return baseUrl.endsWith('/') ? baseUrl.slice(0, -1) : baseUrl;
}

/**
 * seed된 member id를 기준으로 loadtest 사용자 객체를 만든다.
 */
export function getUser(memberId) {
    const userPool = getTargets().userPool;
    const poolStart = userPool.startMemberId ?? 1;
    const poolEndExclusive = poolStart + userPool.count;

    if (memberId < poolStart || memberId >= poolEndExclusive) {
        throw new Error(`Member ${memberId} is out of configured userPool range`);
    }

    return {
        memberId,
        email: formatPattern(userPool.emailPattern, memberId),
        nickname: formatPattern(userPool.nicknamePattern, memberId),
        password: userPool.password
    };
}

/**
 * 명시적 배열 또는 range 객체의 전체 개수를 반환한다.
 */
export function getRangeCount(range, label = 'range') {
    if (Array.isArray(range)) {
        if (range.length === 0) {
            throw new Error(`${label} must not be empty`);
        }
        return range.length;
    }

    return requireRange(range, label).count;
}

/**
 * 0부터 시작하는 index로 숫자 id를 선택하고, 범위를 벗어나면 실패한다.
 */
export function pickNumericIdByIndex(source, index, label = 'numeric range') {
    if (index < 0) {
        throw new Error(`${label} index must be non-negative`);
    }

    if (Array.isArray(source)) {
        if (index >= source.length) {
            throw new Error(`${label} index ${index} exceeds configured length ${source.length}`);
        }
        return source[index];
    }

    const normalized = requireRange(source, label);
    if (index >= normalized.count) {
        throw new Error(`${label} index ${index} exceeds configured count ${normalized.count}`);
    }
    return normalized.start + index;
}

/**
 * index를 가용 id 범위 안에서 순환시켜 숫자 id를 선택한다.
 */
export function pickNumericIdCircular(source, index, label = 'numeric range') {
    if (index < 0) {
        throw new Error(`${label} index must be non-negative`);
    }

    if (Array.isArray(source)) {
        if (source.length === 0) {
            throw new Error(`${label} must not be empty`);
        }
        return source[index % source.length];
    }

    const normalized = requireRange(source, label);
    return normalized.start + (index % normalized.count);
}

/**
 * k6 VU id를 설정된 range 안의 고유 seed 사용자로 매핑한다.
 */
export function pickUserByVuId(range, vuId, label = 'user range') {
    const index = vuId - 1;
    if (index < 0) {
        throw new Error(`VU ${vuId} is invalid for ${label}`);
    }

    const normalized = requireRange(range, label);
    if (index >= normalized.count) {
        throw new Error(`VU ${vuId} exceeds configured ${label} count ${normalized.count}`);
    }

    return getUser(normalized.start + index);
}

/**
 * index를 사용자 range 안에서 순환시켜 seed 사용자를 선택한다.
 */
export function pickUserCircular(range, index, label = 'user range') {
    if (index < 0) {
        throw new Error(`${label} index must be non-negative`);
    }

    const normalized = requireRange(range, label);
    return getUser(normalized.start + (index % normalized.count));
}

/**
 * 숫자 환경변수 override를 읽고 검증한 뒤 fallback을 적용한다.
 */
export function getIntEnv(name, fallback) {
    const value = __ENV[name];
    if (!value) {
        return fallback;
    }

    const parsed = parseInt(value, 10);
    if (Number.isNaN(parsed)) {
        throw new Error(`${name} must be numeric`);
    }
    return parsed;
}

/**
 * 문자열 환경변수 override를 읽고 fallback을 적용한다.
 */
export function getStringEnv(name, fallback) {
    return __ENV[name] || fallback;
}

/**
 * boolean 환경변수 override를 읽고 fallback을 적용한다.
 */
export function getBooleanEnv(name, fallback) {
    const value = __ENV[name];
    if (!value) {
        return fallback;
    }
    return value.toLowerCase() === 'true';
}

/**
 * 시나리오 기본값, target profile 값, 환경변수 override를 하나의 profile로 합친다.
 */
export function getScenarioProfile(scenarioName, defaults = {}) {
    const profileName = getStringEnv('LOAD_PROFILE', 'smoke');
    const profile = getTargets().profiles?.[profileName]?.[scenarioName] || {};

    return {
        rate: getIntEnv('RATE', profile.rate ?? defaults.rate ?? 1),
        duration: getStringEnv('DURATION', profile.duration ?? defaults.duration ?? '30s'),
        vus: getIntEnv('VUS', profile.vus ?? profile.preAllocatedVUs ?? defaults.vus ?? defaults.preAllocatedVUs ?? 1),
        preAllocatedVUs: getIntEnv(
            'PRE_ALLOCATED_VUS',
            profile.preAllocatedVUs ?? profile.vus ?? defaults.preAllocatedVUs ?? defaults.vus ?? 1
        ),
        maxVUs: getIntEnv('MAX_VUS', profile.maxVUs ?? defaults.maxVUs ?? profile.vus ?? defaults.vus ?? 1),
        iterations: getIntEnv('ITERATIONS', profile.iterations ?? defaults.iterations ?? 1),
        maxDuration: getStringEnv('MAX_DURATION', profile.maxDuration ?? defaults.maxDuration ?? '5m')
    };
}

/**
 * constant-arrival-rate 시나리오용 k6 options를 만든다.
 */
export function constantArrivalOptions(scenarioName, execName, defaults, thresholds = {}) {
    const profile = getScenarioProfile(scenarioName, defaults);
    return {
        noCookiesReset: getBooleanEnv('NO_COOKIES_RESET', true),
        scenarios: {
            [execName]: {
                executor: 'constant-arrival-rate',
                rate: profile.rate,
                timeUnit: '1s',
                duration: profile.duration,
                preAllocatedVUs: profile.preAllocatedVUs,
                maxVUs: profile.maxVUs
            }
        },
        thresholds
    };
}

/**
 * shared-iterations 시나리오용 k6 options를 만든다.
 */
export function sharedIterationsOptions(scenarioName, execName, defaults, thresholds = {}) {
    const profile = getScenarioProfile(scenarioName, defaults);
    return {
        noCookiesReset: getBooleanEnv('NO_COOKIES_RESET', true),
        scenarios: {
            [execName]: {
                executor: 'shared-iterations',
                vus: profile.vus,
                iterations: profile.iterations,
                maxDuration: profile.maxDuration
            }
        },
        thresholds
    };
}

/**
 * per-vu-iterations 시나리오용 k6 options를 만든다.
 */
export function perVuIterationsOptions(scenarioName, execName, defaults, thresholds = {}) {
    const profile = getScenarioProfile(scenarioName, defaults);
    return {
        noCookiesReset: getBooleanEnv('NO_COOKIES_RESET', true),
        scenarios: {
            [execName]: {
                executor: 'per-vu-iterations',
                vus: profile.vus,
                iterations: profile.iterations,
                maxDuration: profile.maxDuration
            }
        },
        thresholds
    };
}
