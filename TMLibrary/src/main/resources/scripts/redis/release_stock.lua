-- ============================================================
-- release_stock.lua — 释放预占库存（取消订单 / 超时关单时调用）
-- KEYS[1] = book hash key
-- ARGV[1] = 要释放的数量
-- 返回值：
--   >= 0 : 释放成功，返回释放后的 reserved 剩余量
--   -1   : book 在 Redis 中不存在
--   -2   : reserved 不足（数据异常，正常情况下不该出现）
-- 副作用：reserved -= qty, stock += qty
-- ============================================================
local reserved = redis.call('HGET', KEYS[1], 'reserved')
if not reserved then
    return -1
end
local r = tonumber(reserved)
local need = tonumber(ARGV[1])
if r == nil or need == nil then
    return -1
end
if r < need then
    return -2
end
redis.call('HINCRBY', KEYS[1], 'reserved', -need)
redis.call('HINCRBY', KEYS[1], 'stock', need)
return r - need
