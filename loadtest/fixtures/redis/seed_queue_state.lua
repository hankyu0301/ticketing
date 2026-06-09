local metaKey = KEYS[1]
local activeKey = KEYS[2]
local waitKey = KEYS[3]
local scoreKey = KEYS[4]
local processingKey = KEYS[5]

local scheduleId = ARGV[1]
local activeStartMemberId = tonumber(ARGV[2])
local activeCount = tonumber(ARGV[3])
local waitingStartMemberId = tonumber(ARGV[4])
local waitingCount = tonumber(ARGV[5])
local activeDurationMs = tonumber(ARGV[6])
local reservationOpenAtMs = ARGV[7]
local concertDateMs = ARGV[8]

local time = redis.call('TIME')
local nowMs = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
local activeUntil = nowMs + activeDurationMs

redis.call('DEL', activeKey, waitKey, scoreKey)
redis.call('ZREM', processingKey, scheduleId)
redis.call('HSET', metaKey,
    'reservationOpenAtMs', reservationOpenAtMs,
    'concertDateMs', concertDateMs
)

for offset = 0, activeCount - 1 do
    redis.call('ZADD', activeKey, activeUntil, tostring(activeStartMemberId + offset))
end

for offset = 0, waitingCount - 1 do
    redis.call('ZADD', waitKey, offset + 1, tostring(waitingStartMemberId + offset))
end

if waitingCount > 0 then
    redis.call('SET', scoreKey, waitingCount)
    redis.call('ZADD', processingKey, nowMs, scheduleId)
end

return { scheduleId, activeCount, waitingCount, activeUntil }
