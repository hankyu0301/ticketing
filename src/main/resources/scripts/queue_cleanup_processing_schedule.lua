-- KEYS[1]: waiting queue key
-- KEYS[2]: active queue key
-- KEYS[3]: processing schedules set key
-- ARGV[1]: schedule id

local waitingCount = redis.call('ZCARD', KEYS[1])
local activeCount = redis.call('ZCARD', KEYS[2])

if waitingCount == 0 and activeCount == 0 then
    return redis.call('SREM', KEYS[3], ARGV[1])
end

return 0
