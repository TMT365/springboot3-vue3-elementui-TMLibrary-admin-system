-- ============================================================
-- confirm_stock.lua — 确认扣减（支付成功时调用）
-- KEYS[1] = book hash key
-- ARGV[1] = 要确认扣减的数量（来自预占的数量）
-- 返回值：
--   >= 0 : 确认成功，返回确认后的 reserved 剩余量
--   -1   : book 在 Redis 中不存在
--   -2   : reserved 不足（数据异常）
-- 副作用：reserved -= qty
-- 注意：stock 在预占时已经减过，这里不动 stock — 库存真扣 = 预占时就完成了
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
return r - need
