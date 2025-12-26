-- KEYS[1]: 대기열 Key (queue:wait:{scheduleId})
-- KEYS[2]: 활성열 Key (queue:active:{scheduleId})
-- ARGV[1]: 최대 허용 인원 (MAX_ACTIVE)
-- ARGV[2]: 한 번에 입장시킬 인원 (BATCH_SIZE)
-- ARGV[3]: 활성 만료 시간 (현재시간 + 5분)
-- ARGV[4]: 현재 시간 (청소용)

local waitingKey = KEYS[1]
local activeKey = KEYS[2]
local maxActive = tonumber(ARGV[1])
local batchSize = tonumber(ARGV[2])
local expireAt = ARGV[3]
local now = ARGV[4]

-- 1. 만료된 활성 멤버 청소 (공간 확보)
redis.call('ZREMRANGEBYSCORE', activeKey, '-inf', now)

-- 2. 현재 활성 인원 확인
local currentActive = redis.call('ZCARD', activeKey)
local available = maxActive - currentActive

-- 자리가 없으면 빈 리스트 반환
if available <= 0 then
    return {}
end

-- 3. 입장시킬 인원 수 계산
local moveCount = available
if moveCount > batchSize then
    moveCount = batchSize
end

-- 4. 대기열에서 상위 N명 가져오기
local members = redis.call('ZRANGE', waitingKey, 0, moveCount - 1)

-- 대기자가 없으면 빈 리스트 반환
if #members == 0 then
    return {}
end

-- 5. 이동 처리 (Wait 삭제 -> Active 추가)
for i, member in ipairs(members) do
    redis.call('ZREM', waitingKey, member)
    redis.call('ZADD', activeKey, expireAt, member)
end

-- 6. 입장한 유저 목록 반환 (Java 로깅용)
return members