-- Atomically maintain processing-time windows and return the same snapshot on retry.
local now, id, amount = tonumber(ARGV[1]), ARGV[2], tonumber(ARGV[3])
local cached = redis.call('HGET', KEYS[9], id)
if cached then return cjson.decode(cached) end

-- Expire the hour before deleting the amount records needed to subtract its sum.
for _, expired in ipairs(redis.call('ZRANGEBYSCORE', KEYS[5], '-inf', now - 3600000)) do
    local oldAmount = tonumber(redis.call('HGET', KEYS[2], expired) or '0')
    redis.call('HINCRBYFLOAT', KEYS[4], 'hour', -oldAmount)
    redis.call('ZREM', KEYS[5], expired)
end
for _, expired in ipairs(redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', now - 86400000)) do
    local oldAmount = tonumber(redis.call('HGET', KEYS[2], expired) or '0')
    redis.call('HINCRBYFLOAT', KEYS[4], 'day', -oldAmount)
    redis.call('HDEL', KEYS[2], expired)
    redis.call('HDEL', KEYS[9], expired)
    redis.call('ZREM', KEYS[3], expired)
    redis.call('ZREM', KEYS[1], expired)
end
redis.call('ZREMRANGEBYSCORE', KEYS[6], '-inf', now - 3600000)
redis.call('ZREMRANGEBYSCORE', KEYS[7], '-inf', now - 604800000)
redis.call('ZREMRANGEBYSCORE', KEYS[8], '-inf', now - 604800000)

-- Compare to prior transactions: including the current amount masks sudden spikes.
local previousCount = redis.call('ZCARD', KEYS[1])
local previousSum = tonumber(redis.call('HGET', KEYS[4], 'day') or '0')
local average = previousCount > 0 and previousSum / previousCount or amount
local deviation = average > 0 and amount / average or 1
local knownDevice = redis.call('ZSCORE', KEYS[7], ARGV[5]) and 1 or 0
local knownLocation = redis.call('ZSCORE', KEYS[8], ARGV[6]) and 1 or 0

redis.call('ZADD', KEYS[1], now, id)
redis.call('HSET', KEYS[2], id, amount)
redis.call('ZADD', KEYS[3], amount, id)
redis.call('HINCRBYFLOAT', KEYS[4], 'day', amount)
local hourSum = redis.call('HINCRBYFLOAT', KEYS[4], 'hour', amount)
redis.call('ZADD', KEYS[5], now, id)
redis.call('ZADD', KEYS[6], now, ARGV[4])
redis.call('ZADD', KEYS[7], now, ARGV[5])
redis.call('ZADD', KEYS[8], now, ARGV[6])

local maximum = redis.call('ZREVRANGE', KEYS[3], 0, 0, 'WITHSCORES')
local result = {
    tostring(redis.call('ZCOUNT', KEYS[1], '(' .. (now - 60000), '+inf')),
    tostring(redis.call('ZCOUNT', KEYS[1], '(' .. (now - 600000), '+inf')),
    tostring(redis.call('ZCARD', KEYS[5])), tostring(hourSum),
    tostring(average), tostring(deviation), tostring(knownDevice), tostring(knownLocation),
    tostring(redis.call('ZCARD', KEYS[6])), maximum[2]
}
redis.call('HSET', KEYS[9], id, cjson.encode(result))
for i, key in ipairs(KEYS) do
    redis.call('PEXPIRE', key, (i == 7 or i == 8) and 604800000 or 86400000)
end
return result
