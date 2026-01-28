-- KEYS[1]: 대기열 Key (queue:wait:{scheduleId})
-- KEYS[2]: 활성열 Key (queue:active:{scheduleId})
-- ARGV[1]: 사용자 ID (memberId)
-- ARGV[2]: 현재 시간 (score/timestamp)
-- ARGV[3]: 최대 허용 인원 (limit)
-- ARGV[4]: 활성 만료 시간 (expireAt)

local waitKey = KEYS[1]
local activeKey = KEYS[2]
local memberId = ARGV[1]
local limit = tonumber(ARGV[2])
local expireAt = ARGV[3]
local ttl = tonumber(ARGV[4])

-- 1. 현재 활성 인원 체크
local activeCount = redis.call('ZCARD', activeKey)

-- 2. 대기열 인원 체크 (내가 들어가기 전 상태)
local waitCount = redis.call('ZCARD', waitKey)

-- [Free Pass 조건]
-- 활성열에 자리가 있고(activeCount < limit) AND 대기열이 비어있으면(waitCount == 0)
if activeCount < limit and waitCount == 0 then
    -- 즉시 활성열 등록
    redis.call('ZADD', activeKey, expireAt, memberId)
    redis.call('EXPIRE', activeKey, ttl)
    return -100 -- 입장 성공 코드
end

local seqKey = "queue:score:" .. string.match(waitKey, "queue:wait:(.*)")
local score = redis.call('INCR', seqKey)
redis.call('EXPIRE', seqKey, ttl)

-- [일반 대기 진입]
-- 자리가 없거나 이미 줄 선 사람이 있으면 대기열로
redis.call('ZADD', waitKey, 'NX', score, memberId)
redis.call('EXPIRE', waitKey, ttl)
-- 내 순번 반환
local rank = redis.call('ZRANK', waitKey, memberId)

if rank == false then
    return -1
end

return rank