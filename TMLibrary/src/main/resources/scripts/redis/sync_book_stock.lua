-- ============================================================
-- sync_book_stock.lua — 管理端改库存后同步 Redis(保留在途预占)
-- KEYS[1] = book inventory hash key
-- ARGV[1] = DB 中的权威库存(books.stock_quantity)
-- 返回值：
--   0  : hash 不存在 — 无需同步(下次预热会从 DB 读取最新值)
--   >=0: 已同步,返回新的可用库存
-- 副作用：stock = DB.stock_quantity - reserved(负数截断为 0)
--
-- 为什么不能直接 DEL 整个 hash：
--   hash 里存着 reserved(在途订单已预占的量)。
--   直接删除会让所有未支付订单的预占"消失",
--   这些订单付款时会被 DB 守卫拒绝(库存不足),真实客户付不了款。
--
-- 不变式(整个双轨库存模型的基础)：
--   Redis(stock + reserved) == DB.stock_quantity
-- 本脚本在 reserved 保持不变的前提下,反推 stock,从而维持该不变式。
-- ============================================================
local key = KEYS[1]

if redis.call('EXISTS', key) == 0 then
    return 0
end

local reserved = tonumber(redis.call('HGET', key, 'reserved') or '0')
local dbStock = tonumber(ARGV[1]) or 0

-- 管理端可能把库存改到低于在途预占量(例如已卖出 10 本在下单中,却把库存改成 5)
-- 此时可用库存按 0 计,不能出现负数
local available = dbStock - reserved
if available < 0 then
    available = 0
end

redis.call('HSET', key, 'stock', available)
return available
