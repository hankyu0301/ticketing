-- KEYS[1]: waiting queue key
-- KEYS[2]: active queue key
-- KEYS[3]: processing schedules zset key
-- ARGV[1]: schedule id
-- ARGV[2]: max active members
-- ARGV[3]: current timestamp (ms)

local waitingKey = KEYS[1]
local activeKey = KEYS[2]
local processingKey = KEYS[3]
local scheduleId = ARGV[1]
local maxActive = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

redis.call('ZREMRANGEBYSCORE', activeKey, '-inf', now)

local waitingCount = redis.call('ZCARD', waitingKey)
if waitingCount == 0 then
    return redis.call('ZREM', processingKey, scheduleId)
end

local activeCount = redis.call('ZCARD', activeKey)
local currentScore = redis.call('ZSCORE', processingKey, scheduleId)

if activeCount < maxActive then
    if currentScore and tonumber(currentScore) <= now then
        return 0
    end

    redis.call('ZADD', processingKey, now, scheduleId)
    return 1
end

local earliestActive = redis.call('ZRANGE', activeKey, 0, 0, 'WITHSCORES')
if not earliestActive[2] then
    if currentScore and tonumber(currentScore) <= now then
        return 0
    end

    redis.call('ZADD', processingKey, now, scheduleId)
    return 1
end

local nextProcessAt = tonumber(earliestActive[2])
if currentScore and tonumber(currentScore) == nextProcessAt then
    return 0
end

redis.call('ZADD', processingKey, nextProcessAt, scheduleId)
return 1
