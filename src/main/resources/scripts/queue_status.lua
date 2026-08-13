-- KEYS[1]: waiting queue key
-- KEYS[2]: active queue key
-- KEYS[3]: schedule meta key
-- ARGV[1]: member id
-- ARGV[2]: current timestamp (ms)

local metaOpenAt = redis.call('HGET', KEYS[3], 'reservationOpenAtMs')
local metaConcertDate = redis.call('HGET', KEYS[3], 'concertDateMs')
if not metaOpenAt or not metaConcertDate then
    return -2
end

if tonumber(metaConcertDate) <= tonumber(ARGV[2]) then
    return -4
end

if tonumber(metaOpenAt) > tonumber(ARGV[2]) then
    return -3
end

local activeScore = redis.call('ZSCORE', KEYS[2], ARGV[1])

if activeScore then
    if tonumber(activeScore) <= tonumber(ARGV[2]) then
        redis.call('ZREM', KEYS[2], ARGV[1])
    else
        return -100
    end
end

local rank = redis.call('ZRANK', KEYS[1], ARGV[1])
if rank == false then
    return -1
end

return rank
