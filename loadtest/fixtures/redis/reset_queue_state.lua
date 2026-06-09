local metaKey = KEYS[1]
local activeKey = KEYS[2]
local waitKey = KEYS[3]
local scoreKey = KEYS[4]
local processingKey = KEYS[5]
local scheduleId = ARGV[1]

redis.call('DEL', metaKey, activeKey, waitKey, scoreKey)
redis.call('ZREM', processingKey, scheduleId)

return scheduleId
