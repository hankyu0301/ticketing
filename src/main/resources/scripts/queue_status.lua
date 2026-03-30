-- KEYS[1]: waiting queue key
-- KEYS[2]: active queue key
-- ARGV[1]: member id
-- ARGV[2]: current timestamp (ms)

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
