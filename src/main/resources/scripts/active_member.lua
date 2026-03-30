-- KEYS[1]: waiting queue key
-- KEYS[2]: active queue key
-- ARGV[1]: max active members
-- ARGV[2]: batch size
-- ARGV[3]: active expiration timestamp (ms)
-- ARGV[4]: current timestamp (ms)

local waitingKey = KEYS[1]
local activeKey = KEYS[2]
local maxActive = tonumber(ARGV[1])
local batchSize = tonumber(ARGV[2])
local expireAt = ARGV[3]
local now = ARGV[4]

redis.call('ZREMRANGEBYSCORE', activeKey, '-inf', now)

local currentActive = redis.call('ZCARD', activeKey)
local available = maxActive - currentActive

if available <= 0 then
    return {}
end

local moveCount = available
if moveCount > batchSize then
    moveCount = batchSize
end

local members = redis.call('ZRANGE', waitingKey, 0, moveCount - 1)
if #members == 0 then
    return {}
end

for _, member in ipairs(members) do
    redis.call('ZREM', waitingKey, member)
    redis.call('ZADD', activeKey, expireAt, member)
end

return members
