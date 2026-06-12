local metaKey = KEYS[1]
local activeKey = KEYS[2]
local waitKey = KEYS[3]
local scoreKey = KEYS[4]
local processingKey = KEYS[5]
local unavailableSetKey = KEYS[6]
local scheduleId = ARGV[1]

local unavailableSeatIds = redis.call('SMEMBERS', unavailableSetKey)
for _, seatId in ipairs(unavailableSeatIds) do
    redis.call('DEL', 'seat:unavailable:{' .. scheduleId .. '}:' .. seatId)
end

redis.call('DEL', metaKey, activeKey, waitKey, scoreKey, unavailableSetKey)
redis.call('ZREM', processingKey, scheduleId)

return scheduleId
