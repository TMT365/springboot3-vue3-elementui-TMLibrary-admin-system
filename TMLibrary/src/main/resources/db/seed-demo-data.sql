-- ============================================================================
-- seed-demo-data.sql — 演示/测试数据
--
-- 用途:让仪表盘统计图(销量趋势 / 新增用户 / 每本书销量 Top / 订单状态分布)
--       有数据可看。数据分布刻意铺开到最近 28 天,覆盖 4 种订单状态。
--
-- 执行:
--   mysql -u root -p tmlibrary < src/main/resources/db/seed-demo-data.sql
--
-- 幂等性:
--   - users / books 用固定 username / isbn + INSERT IGNORE,重复执行不新增
--   - orders 用固定 order_number(9e15 段)+ INSERT IGNORE
--   - order_items 没有业务唯一键,重复执行会翻倍 —— 所以**先 DELETE 本脚本
--     产生的订单明细**(仅限 9e15 号段)再重建,等价于"重置演示订单"
--   - 不触碰任何非 9e15 号段的真实订单
--
-- ⚠️ 仅用于开发/演示环境:
--     - demo 账号密码统一为 Demo@123456(BCrypt 哈希写死在下面)
--     - 生产环境不要执行本脚本
-- ============================================================================

USE `tmlibrary`;

SET @DEMO_HASH = '$2a$10$UxlT/FTzHJTuF3NKN8Vz9.BGo2O46l7kPzWdHv7zlUuA7dhVG3JKi';
SET @ORDER_MIN  = 9000000000000001;
SET @ORDER_MAX  = 9000000000000025;

-- ---------------------------------------------------------------------------
-- 1. 演示用户(5 个,注册时间铺开到最近 26 天 → 支撑"新增用户趋势")
--    password 全部是 Demo@123456(对应上面的 BCrypt 哈希)
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO users
    (username, password_hash, email, phone_number, role, status, created_time, updated_time)
VALUES
    ('demo_alice', @DEMO_HASH, 'alice@demo.local', '13800000001', 0, 0, DATE_SUB(NOW(), INTERVAL 26 DAY), DATE_SUB(NOW(), INTERVAL 26 DAY)),
    ('demo_bob',   @DEMO_HASH, 'bob@demo.local',   '13800000002', 0, 0, DATE_SUB(NOW(), INTERVAL 19 DAY), DATE_SUB(NOW(), INTERVAL 19 DAY)),
    ('demo_carol', @DEMO_HASH, 'carol@demo.local', '13800000003', 0, 0, DATE_SUB(NOW(), INTERVAL 11 DAY), DATE_SUB(NOW(), INTERVAL 11 DAY)),
    ('demo_admin', @DEMO_HASH, 'admin@demo.local', '13800000004', 1, 0, DATE_SUB(NOW(), INTERVAL  8 DAY), DATE_SUB(NOW(), INTERVAL  8 DAY)),
    ('demo_dave',  @DEMO_HASH, 'dave@demo.local',  '13800000005', 0, 0, DATE_SUB(NOW(), INTERVAL  4 DAY), DATE_SUB(NOW(), INTERVAL  4 DAY));

SET @u_tmt   = (SELECT id FROM users WHERE username = 'tmt'        LIMIT 1);
SET @u_alice = (SELECT id FROM users WHERE username = 'demo_alice' LIMIT 1);
SET @u_bob   = (SELECT id FROM users WHERE username = 'demo_bob'   LIMIT 1);
SET @u_carol = (SELECT id FROM users WHERE username = 'demo_carol' LIMIT 1);
SET @u_dave  = (SELECT id FROM users WHERE username = 'demo_dave'  LIMIT 1);

-- ---------------------------------------------------------------------------
-- 2. 演示图书(12 本,价格 35~139,库存各异)
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO books
    (title, author, isbn, price, published_date, stock_quantity, created_time, updated_time)
VALUES
    ('深入理解计算机系统',      'Randal E. Bryant',   '9787111544937', 139.00, '2016-11-01', 25, DATE_SUB(NOW(), INTERVAL 30 DAY), NOW()),
    ('算法导论(第3版)',        'Thomas H. Cormen',   '9787111407010', 128.00, '2013-01-01', 18, DATE_SUB(NOW(), INTERVAL 29 DAY), NOW()),
    ('代码整洁之道',            'Robert C. Martin',   '9787115216878',  59.00, '2010-01-01', 32, DATE_SUB(NOW(), INTERVAL 28 DAY), NOW()),
    ('设计模式',                'Erich Gamma',        '9787111075752',  65.00, '2000-09-01', 12, DATE_SUB(NOW(), INTERVAL 26 DAY), NOW()),
    ('重构:改善既有代码的设计', 'Martin Fowler',      '9787115509581', 118.00, '2019-04-01', 20, DATE_SUB(NOW(), INTERVAL 24 DAY), NOW()),
    ('Java 并发编程实战',       'Brian Goetz',        '9787111370048',  79.00, '2012-02-01', 15, DATE_SUB(NOW(), INTERVAL 22 DAY), NOW()),
    ('深入理解 Java 虚拟机',    '周志明',             '9787111543246',  99.00, '2024-01-15', 40, DATE_SUB(NOW(), INTERVAL 20 DAY), NOW()),
    ('百年孤独',                '加西亚·马尔克斯',    '9787544253994',  55.00, '2011-06-01', 30, DATE_SUB(NOW(), INTERVAL 18 DAY), NOW()),
    ('活着',                    '余华',               '9787506365437',  35.00, '2012-08-01', 50, DATE_SUB(NOW(), INTERVAL 15 DAY), NOW()),
    ('三体',                    '刘慈欣',             '9787536692930',  93.00, '2008-01-01', 22, DATE_SUB(NOW(), INTERVAL 12 DAY), NOW()),
    ('人类简史',                '尤瓦尔·赫拉利',      '9787508647357',  68.00, '2014-11-01', 28, DATE_SUB(NOW(), INTERVAL  9 DAY), NOW()),
    ('沉默的大多数',            '王小波',             '9787530216552',  42.00, '2017-03-01', 16, DATE_SUB(NOW(), INTERVAL  6 DAY), NOW());

-- ---------------------------------------------------------------------------
-- 3. 演示订单(25 笔,时间铺开到最近 28 天)
--    状态:1=已支付 16 笔 / 0=待支付 3 笔(今天,未过期)/ 2=已取消 3 笔 / 3=超时 3 笔
--    total_amount 先填 0,第 5 步从订单明细回填 —— 保证金额与明细严格一致
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO orders
    (order_number, user_id, total_amount, order_status, expire_time, created_time, updated_time)
VALUES
    -- 已支付:铺满最近 28 天
    (@ORDER_MIN +  0, @u_alice, 0, 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL 28 DAY), INTERVAL 30 MINUTE), DATE_SUB(NOW(), INTERVAL 28 DAY), DATE_SUB(NOW(), INTERVAL 28 DAY)),
    (@ORDER_MIN +  1, @u_bob,   0, 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL 26 DAY), INTERVAL 30 MINUTE), DATE_SUB(NOW(), INTERVAL 26 DAY), DATE_SUB(NOW(), INTERVAL 26 DAY)),
    (@ORDER_MIN +  2, @u_tmt,   0, 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL 24 DAY), INTERVAL 30 MINUTE), DATE_SUB(NOW(), INTERVAL 24 DAY), DATE_SUB(NOW(), INTERVAL 24 DAY)),
    (@ORDER_MIN +  3, @u_carol, 0, 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL 22 DAY), INTERVAL 30 MINUTE), DATE_SUB(NOW(), INTERVAL 22 DAY), DATE_SUB(NOW(), INTERVAL 22 DAY)),
    (@ORDER_MIN +  4, @u_alice, 0, 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL 20 DAY), INTERVAL 30 MINUTE), DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 20 DAY)),
    (@ORDER_MIN +  5, @u_dave,  0, 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL 18 DAY), INTERVAL 30 MINUTE), DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY)),
    (@ORDER_MIN +  6, @u_bob,   0, 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL 16 DAY), INTERVAL 30 MINUTE), DATE_SUB(NOW(), INTERVAL 16 DAY), DATE_SUB(NOW(), INTERVAL 16 DAY)),
    (@ORDER_MIN +  7, @u_alice, 0, 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL 14 DAY), INTERVAL 30 MINUTE), DATE_SUB(NOW(), INTERVAL 14 DAY), DATE_SUB(NOW(), INTERVAL 14 DAY)),
    (@ORDER_MIN +  8, @u_carol, 0, 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL 12 DAY), INTERVAL 30 MINUTE), DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 12 DAY)),
    (@ORDER_MIN +  9, @u_tmt,   0, 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL 10 DAY), INTERVAL 30 MINUTE), DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY)),
    (@ORDER_MIN + 10, @u_bob,   0, 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL  8 DAY), INTERVAL 30 MINUTE), DATE_SUB(NOW(), INTERVAL  8 DAY), DATE_SUB(NOW(), INTERVAL  8 DAY)),
    (@ORDER_MIN + 11, @u_dave,  0, 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL  6 DAY), INTERVAL 30 MINUTE), DATE_SUB(NOW(), INTERVAL  6 DAY), DATE_SUB(NOW(), INTERVAL  6 DAY)),
    (@ORDER_MIN + 12, @u_alice, 0, 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL  5 DAY), INTERVAL 30 MINUTE), DATE_SUB(NOW(), INTERVAL  5 DAY), DATE_SUB(NOW(), INTERVAL  5 DAY)),
    (@ORDER_MIN + 13, @u_carol, 0, 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL  3 DAY), INTERVAL 30 MINUTE), DATE_SUB(NOW(), INTERVAL  3 DAY), DATE_SUB(NOW(), INTERVAL  3 DAY)),
    (@ORDER_MIN + 14, @u_bob,   0, 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL  2 DAY), INTERVAL 30 MINUTE), DATE_SUB(NOW(), INTERVAL  2 DAY), DATE_SUB(NOW(), INTERVAL  2 DAY)),
    (@ORDER_MIN + 15, @u_dave,  0, 1, DATE_ADD(DATE_SUB(NOW(), INTERVAL  1 DAY), INTERVAL 30 MINUTE), DATE_SUB(NOW(), INTERVAL  1 DAY), DATE_SUB(NOW(), INTERVAL  1 DAY)),
    -- 已取消
    (@ORDER_MIN + 16, @u_carol, 0, 2, DATE_ADD(DATE_SUB(NOW(), INTERVAL 21 DAY), INTERVAL 30 MINUTE), DATE_SUB(NOW(), INTERVAL 21 DAY), DATE_SUB(NOW(), INTERVAL 21 DAY)),
    (@ORDER_MIN + 17, @u_alice, 0, 2, DATE_ADD(DATE_SUB(NOW(), INTERVAL 13 DAY), INTERVAL 30 MINUTE), DATE_SUB(NOW(), INTERVAL 13 DAY), DATE_SUB(NOW(), INTERVAL 13 DAY)),
    (@ORDER_MIN + 18, @u_dave,  0, 2, DATE_ADD(DATE_SUB(NOW(), INTERVAL  4 DAY), INTERVAL 30 MINUTE), DATE_SUB(NOW(), INTERVAL  4 DAY), DATE_SUB(NOW(), INTERVAL  4 DAY)),
    -- 超时取消
    (@ORDER_MIN + 19, @u_bob,   0, 3, DATE_SUB(NOW(), INTERVAL 17 DAY), DATE_SUB(NOW(), INTERVAL 17 DAY), DATE_SUB(NOW(), INTERVAL 17 DAY)),
    (@ORDER_MIN + 20, @u_tmt,   0, 3, DATE_SUB(NOW(), INTERVAL 11 DAY), DATE_SUB(NOW(), INTERVAL 11 DAY), DATE_SUB(NOW(), INTERVAL 11 DAY)),
    (@ORDER_MIN + 21, @u_carol, 0, 3, DATE_SUB(NOW(), INTERVAL  7 DAY), DATE_SUB(NOW(), INTERVAL  7 DAY), DATE_SUB(NOW(), INTERVAL  7 DAY)),
    -- 待支付:expire_time 设在未来,避免被 OrderExpireScheduler 立刻关单
    (@ORDER_MIN + 22, @u_alice, 0, 0, DATE_ADD(NOW(), INTERVAL 30 MINUTE), DATE_SUB(NOW(), INTERVAL 5 MINUTE), NOW()),
    (@ORDER_MIN + 23, @u_dave,  0, 0, DATE_ADD(NOW(), INTERVAL 30 MINUTE), DATE_SUB(NOW(), INTERVAL 3 MINUTE), NOW()),
    (@ORDER_MIN + 24, @u_tmt,   0, 0, DATE_ADD(NOW(), INTERVAL 30 MINUTE), DATE_SUB(NOW(), INTERVAL 1 MINUTE), NOW());

-- ---------------------------------------------------------------------------
-- 4. 订单明细
--    先清掉本号段旧明细(保证可重复执行),再用一张"临时映射表"一次性插完:
--      (订单号, isbn, 数量) → JOIN orders / books 拿到真实 id 与快照单价
--    单价取 books.price(下单时快照语义),金额 = quantity × price
-- ---------------------------------------------------------------------------
DELETE oi FROM order_items oi
    JOIN orders o ON o.id = oi.order_id
    WHERE o.order_number BETWEEN @ORDER_MIN AND @ORDER_MAX;

INSERT INTO order_items (order_id, book_id, quantity, price, created_time)
SELECT o.id, b.id, v.qty, b.price, o.created_time
FROM (
    -- 已支付订单的明细 —— 刻意让几本书反复出现,做出销量 Top 的梯度
    SELECT @ORDER_MIN +  0 AS onum, '9787111543246' AS isbn, 2 AS qty UNION ALL  -- 深入理解 JVM ×2
    SELECT @ORDER_MIN +  0, '9787506365437', 1 UNION ALL                          -- 活着 ×1
    SELECT @ORDER_MIN +  1, '9787536692930', 3 UNION ALL                          -- 三体 ×3
    SELECT @ORDER_MIN +  2, '9787111544937', 1 UNION ALL                          -- 深入理解计算机系统 ×1
    SELECT @ORDER_MIN +  2, '9787111407010', 1 UNION ALL                          -- 算法导论 ×1
    SELECT @ORDER_MIN +  3, '9787536692930', 2 UNION ALL                          -- 三体 ×2
    SELECT @ORDER_MIN +  3, '9787508647357', 1 UNION ALL                          -- 人类简史 ×1
    SELECT @ORDER_MIN +  4, '9787115509581', 1 UNION ALL                          -- 重构 ×1
    SELECT @ORDER_MIN +  4, '9787115216878', 2 UNION ALL                          -- 代码整洁之道 ×2
    SELECT @ORDER_MIN +  5, '9787506365437', 4 UNION ALL                          -- 活着 ×4
    SELECT @ORDER_MIN +  6, '9787111543246', 1 UNION ALL                          -- 深入理解 JVM ×1
    SELECT @ORDER_MIN +  6, '9787530216552', 1 UNION ALL                          -- 沉默的大多数 ×1
    SELECT @ORDER_MIN +  7, '9787536692930', 1 UNION ALL                          -- 三体 ×1
    SELECT @ORDER_MIN +  8, '9787508647357', 2 UNION ALL                          -- 人类简史 ×2
    SELECT @ORDER_MIN +  9, '9787111370048', 1 UNION ALL                          -- Java 并发编程实战 ×1
    SELECT @ORDER_MIN + 10, '9787111075752', 1 UNION ALL                          -- 设计模式 ×1
    SELECT @ORDER_MIN + 10, '9787544253994', 2 UNION ALL                          -- 百年孤独 ×2
    SELECT @ORDER_MIN + 11, '9787506365437', 2 UNION ALL                          -- 活着 ×2
    SELECT @ORDER_MIN + 12, '9787536692930', 2 UNION ALL                          -- 三体 ×2
    SELECT @ORDER_MIN + 12, '9787111543246', 1 UNION ALL                          -- 深入理解 JVM ×1
    SELECT @ORDER_MIN + 13, '9787508647357', 1 UNION ALL                          -- 人类简史 ×1
    SELECT @ORDER_MIN + 14, '9787115216878', 1 UNION ALL                          -- 代码整洁之道 ×1
    SELECT @ORDER_MIN + 15, '9787111407010', 1 UNION ALL                          -- 算法导论 ×1
    SELECT @ORDER_MIN + 15, '9787530216552', 2 UNION ALL                          -- 沉默的大多数 ×2
    -- 已取消订单也留明细(状态过滤后不会计入销量,但订单详情页能看)
    SELECT @ORDER_MIN + 16, '9787111544937', 1 UNION ALL
    SELECT @ORDER_MIN + 17, '9787544253994', 1 UNION ALL
    SELECT @ORDER_MIN + 18, '9787111370048', 1 UNION ALL
    -- 超时订单同理
    SELECT @ORDER_MIN + 19, '9787111075752', 1 UNION ALL
    SELECT @ORDER_MIN + 20, '9787506365437', 1 UNION ALL
    SELECT @ORDER_MIN + 21, '9787536692930', 1 UNION ALL
    -- 待支付
    SELECT @ORDER_MIN + 22, '9787115509581', 1 UNION ALL
    SELECT @ORDER_MIN + 23, '9787111543246', 1 UNION ALL
    SELECT @ORDER_MIN + 24, '9787508647357', 3
) v
JOIN orders o ON o.order_number = v.onum
JOIN books  b ON b.isbn = v.isbn;

-- ---------------------------------------------------------------------------
-- 5. 回填订单总额 = 明细合计(与 order_items 严格一致)
-- ---------------------------------------------------------------------------
UPDATE orders o
SET o.total_amount = (
    SELECT COALESCE(SUM(oi.quantity * oi.price), 0)
    FROM order_items oi
    WHERE oi.order_id = o.id
)
WHERE o.order_number BETWEEN @ORDER_MIN AND @ORDER_MAX;

-- ---------------------------------------------------------------------------
-- 6. 自检输出(执行后应看到:12 本书 / 25 笔订单 / 约 35 条明细)
-- ---------------------------------------------------------------------------
SELECT '演示数据就绪' AS info,
       (SELECT COUNT(*) FROM users       WHERE username LIKE 'demo_%') AS demo_users,
       (SELECT COUNT(*) FROM books)                                   AS books,
       (SELECT COUNT(*) FROM orders      WHERE order_number BETWEEN @ORDER_MIN AND @ORDER_MAX) AS demo_orders,
       (SELECT COUNT(*) FROM order_items)                             AS order_items;
