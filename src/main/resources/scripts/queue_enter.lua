-- KEYS[1]: waiting queue key
-- KEYS[2]: active queue key
-- KEYS[3]: sequence key
-- KEYS[4]: schedule meta key
-- ARGV[1]: member id
-- ARGV[2]: max active members
-- ARGV[3]: active expiration timestamp (ms)
-- ARGV[4]: key ttl (seconds)
-- ARGV[5]: current timestamp (ms)

local waitKey = KEYS[1]
local activeKey = KEYS[2]
local seqKey = KEYS[3]
local metaKey = KEYS[4]

local memberId = ARGV[1]
local limit = tonumber(ARGV[2])
local expireAt = ARGV[3]
local ttl = tonumber(ARGV[4])
local now = tonumber(ARGV[5])

local reservationOpenAtMs = redis.call('HGET', metaKey, 'reservationOpenAtMs')
local concertDateMs = redis.call('HGET', metaKey, 'concertDateMs')
if not reservationOpenAtMs or not concertDateMs then
    return -2
end

if tonumber(concertDateMs) <= now then
    return -4
end

if tonumber(reservationOpenAtMs) > now then
    return -3
end

local activeScore = redis.call('ZSCORE', activeKey, memberId)
if activeScore then
    if tonumber(activeScore) <= now then
        redis.call('ZREM', activeKey, memberId)
    else
        return -100
    end
end

local existingRank = redis.call('ZRANK', waitKey, memberId)
if existingRank ~= false then
    return existingRank
end

redis.call('ZREMRANGEBYSCORE', activeKey, '-inf', now)

local activeCount = redis.call('ZCARD', activeKey)
local waitCount = redis.call('ZCARD', waitKey)

if activeCount < limit and waitCount == 0 then
    redis.call('ZADD', activeKey, expireAt, memberId)
    redis.call('EXPIRE', activeKey, ttl)
    return -100
end

local score = redis.call('INCR', seqKey)
redis.call('EXPIRE', seqKey, ttl)

redis.call('ZADD', waitKey, 'NX', score, memberId)
redis.call('EXPIRE', waitKey, ttl)

local rank = redis.call('ZRANK', waitKey, memberId)
if rank == false then
    return -1
end

return rank
