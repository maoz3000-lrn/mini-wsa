-- Adds one event to its client IP's sliding window and returns how many OTHER events of that IP fall in
-- [ts - window, ts]. Runs atomically, so concurrent consumers always see each other's events.
-- KEYS[1] window key for the client IP
-- ARGV[1] event timestamp (epoch ms)   ARGV[2] eventId (set member, makes retries idempotent)
-- ARGV[3] window (ms)                  ARGV[4] max entries kept   ARGV[5] key TTL (ms)
local ts = tonumber(ARGV[1])
redis.call('ZADD', KEYS[1], ts, ARGV[2])
local prior = redis.call('ZCOUNT', KEYS[1], ts - tonumber(ARGV[3]), ts) - 1
redis.call('ZREMRANGEBYRANK', KEYS[1], 0, -(tonumber(ARGV[4]) + 1))
redis.call('PEXPIRE', KEYS[1], ARGV[5])
return prior
