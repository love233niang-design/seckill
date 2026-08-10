-- KEYS[1]: 秒杀库存 Key
-- 返回值：1-预扣成功，0-库存售罄，-1-库存 Key 不存

-- 1. 获取当前秒杀库存
local stock = redis.call('GET', KEYS[1])

-- 2. 库存 Key 不存在，说明库存尚未预热或 Key 已失效
if not stock then
    return -1
end

-- 3. 库存小于等于 0，直接返回售罄，不再继续扣减
if tonumber(stock) <= 0 then
    return 0
end

-- 4. 库存充足，原子递减库存
redis.call('DECR', KEYS[1])
return 1