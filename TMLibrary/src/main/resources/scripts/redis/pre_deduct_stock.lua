-- ============================================================
-- pre_deduct_stock.lua — 库存预占（下单时调用）
-- KEYS[1] = book hash key (e.g., tmlibrary:book:42)
-- ARGV[1] = 要预占的数量
-- 返回值：
--   >= 0 : 预占成功，返回剩余可用库存
--   -1   : book 在 Redis 中不存在（调用方需先 warmUp 再重试）
--   -2   : 库存不足
-- 副作用：stock -= qty, reserved += qty
-- ============================================================
local stock = redis.call('HGET', KEYS[1], 'stock')
if not stock then
    return -1
end
local available = tonumber(stock)
local need = tonumber(ARGV[1])
if available == nil or need == nil then
    return -1
end
if available < need then
    return -2
end
redis.call('HINCRBY', KEYS[1], 'stock', -need)
redis.call('HINCRBY', KEYS[1], 'reserved', need)
return available - need
