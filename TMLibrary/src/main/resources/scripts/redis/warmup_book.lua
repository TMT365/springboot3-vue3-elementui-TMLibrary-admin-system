-- ============================================================
-- warmup_book.lua — 冷 key 预热(下单遇到 book hash 不存在时调用)
-- KEYS[1] = book hash key (e.g., tmlibrary:book:byId:42:inventory)
-- ARGV[1] = bookId
-- ARGV[2] = title
-- ARGV[3] = price
-- ARGV[4] = stock (来自 DB 的 stock_quantity)
-- 返回值：
--   1 : 已尝试初始化(至少写入了一个字段)
--   0 : 所有字段已存在,无需初始化
-- 副作用：逐字段 HSETNX,不覆盖已存在的字段
-- ============================================================
-- 为什么用 HSETNX 而不是 HSET：
--   并发预热同一 book 时,A 和 B 都从 DB 读到 stock=10。
--   用 HSET  → B 覆盖 A 已写的 reserved,丢失 A 已预占的量。
--   用 HSETNX → 已存在则跳过,保留先到者的值。
--
-- 为什么放 Lua：
--   5 个独立 HSETNX 跨 5 个 RTT,期间可能被其他客户端插入写操作;
--   Lua 内多条命令由 Redis 单线程连续执行,整段不可打断(真原子)。
-- ============================================================
local key = KEYS[1]
local wrote = 0

if redis.call('HSETNX', key, 'id', ARGV[1]) == 1 then wrote = 1 end
if redis.call('HSETNX', key, 'title', ARGV[2]) == 1 then wrote = 1 end
if redis.call('HSETNX', key, 'price', ARGV[3]) == 1 then wrote = 1 end
if redis.call('HSETNX', key, 'stock', ARGV[4]) == 1 then wrote = 1 end
if redis.call('HSETNX', key, 'reserved', '0') == 1 then wrote = 1 end

return wrote
