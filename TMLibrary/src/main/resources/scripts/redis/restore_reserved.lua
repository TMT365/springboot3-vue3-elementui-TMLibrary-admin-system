-- ============================================================
-- restore_reserved.lua — 归还"已确认"的预占(事务回滚时补偿)
-- KEYS[1] = book hash key
-- ARGV[1] = 要归还的数量
-- 返回值：
--   >= 0 : 归还成功，返回归还后的 reserved
--   -1   : book 在 Redis 中不存在
-- 副作用：reserved += qty
--
-- 为什么需要它：
--   confirm_stock.lua 把 reserved 减掉了,但如果 DB 事务随后回滚
--   (订单并没有真的支付成功),Redis 侧就少记了这笔预占 ——
--   Redis 可用量会比真实值少,别的用户会看到"库存不足"。
--   这个脚本是 confirm 的逆操作,用在事务回滚的补偿钩子里。
--
-- 注意：只加 reserved,不动 stock ——
--   confirm 时 stock 也没动过(预占时就减过了),
--   所以撤销 confirm 同样不需要碰 stock。
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
redis.call('HINCRBY', KEYS[1], 'reserved', need)
return r + need
