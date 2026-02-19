-- KEYS[1]: 활성열 키
-- ARGV[1]: 유저 ID (memberId)
-- ARGV[2]: 현재 시간 (Unix Timestamp)
-- ARGV[3]: 연장할 새로운 만료 시간

local activeKey = KEYS[1]
local member = ARGV[1]
local currentTime = tonumber(ARGV[2])
local newExpireAt = tonumber(ARGV[3])

-- 1. 유저의 현재 만료 예정 시각(Score) 조회
local score = redis.call('ZSCORE', activeKey, member)

-- 2. 유저가 아예 없으면 -> 거절 (false: 0)
if not score then
    return 0
end

-- 3. 유저가 있지만, 이미 만료 시간이 지났다면
-- 스케줄러가 아직 안 지웠더라도, 여기서 바로 차단하고 삭제해버림 (좀비 방지)
if tonumber(score) < currentTime then
    redis.call('ZREM', activeKey, member)
    return 0
end

-- 4. 살아있는 유저라면 -> 시간 연장 (Update)
redis.call('ZADD', activeKey, newExpireAt, member)

return 1 -- 성공 (true: 1)