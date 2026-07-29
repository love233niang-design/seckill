-- 原子性地检查登录失败次数并累加
-- 如果失败次数已达上限，返回 -1；
-- 如果未达上限，累加失败次数，并返回累加后的值
--
-- KEYS[1]: 登录失败计数的 Redis Key（如 login_fail_count:13800138001）
-- ARGV[1]: 登录失败次数上限（如 5）
-- ARGV[2]: 锁定时间，单位秒（如 1800，即 30 分钟）

local currentCount = tonumber(redis.call('GET', KEYS[1]) or '0')

if currentCount >= tonumber(ARGV[1]) then
    -- 已达上限：刷新过期时间，重新计时 30 分钟（锁满 30 分钟）
    redis.call('EXPIRE', KEYS[1], ARGV[2])
    return -1
end

local newCount = redis.call('INCR', KEYS[1])

if newCount == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[2])
end

return newCount
