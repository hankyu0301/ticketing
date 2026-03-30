-- KEYS[1]: active queue key
-- ARGV[1]: member id
-- ARGV[2]: current timestamp (ms)
-- ARGV[3]: next expiration timestamp (ms)

local activeKey = KEYS[1]
local member = ARGV[1]
local currentTime = tonumber(ARGV[2])
local newExpireAt = tonumber(ARGV[3])

local score = redis.call('ZSCORE', activeKey, member)
if not score then
    return 0
end

if tonumber(score) <= currentTime then
    redis.call('ZREM', activeKey, member)
    return 0
end

redis.call('ZADD', activeKey, newExpireAt, member)
return 1
