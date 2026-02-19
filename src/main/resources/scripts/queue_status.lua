-- KEYS[1]: 대기열 Key (queue:{id}:wait)
-- KEYS[2]: 활성열 Key (queue{id}:active)
-- ARGV[1]: 사용자 ID (memberId)

-- 1. 활성 상태(이미 입장함)인지 확인
-- 활성열은 ZSET이며 Score는 만료시간. 존재 여부만 확인하면 됨.
local activeScore = redis.call('ZSCORE', KEYS[2], ARGV[1])

if activeScore then
    return -100 -- 입장 완료 상태
end

-- 2. 대기열 순번 조회
local rank = redis.call('ZRANK', KEYS[1], ARGV[1])

if rank == false then
    return -1 -- 대기열/활성열 모두 없음
end

return rank