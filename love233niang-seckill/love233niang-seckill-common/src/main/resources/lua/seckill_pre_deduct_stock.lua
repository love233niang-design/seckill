-- KEYS[1]: 秒杀库存 Key
-- KEYS[2]: 用户购买标记 Key
-- ARGV[1]: 用户购买标记的过期时间（秒）
-- 返回值：1-预扣成功，0-库存售罄，-1-库存 Key 不存在

-- 1. 用户购买标记已存在，说明该用户已经参与过本场秒杀
if redis.call('GET', KEYS[2]) == 1 then
    return 2
end

-- 2. 获取当前秒杀库存
local stock = redis.call('GET', KEYS[1])

-- 3. 库存 Key 不存在，说明库存尚未预热或 Key 已失效
if not stock then
    return -1
end

-- 4. 库存小于等于 0，直接返回售罄，不再继续扣减
if tonumber(stock) <= 0 then
    return 0
end

-- 5. 写入用户购买标记，过期时间覆盖整个秒杀活动周期
redis.call('SET', KEYS[2], 1, 'EX', ARGV[1])

-- 6. 库存充足，原子递减库存
redis.call('DECR', KEYS[1])
return 1