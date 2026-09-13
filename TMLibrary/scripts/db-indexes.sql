-- ============================================================
-- TMLibrary 索引补强
--
-- 背景:列表/搜索接口存在大量组合筛选,而库上未见对应索引,
--       数据量上升后会退化为全表扫描。
--
-- 使用方式:按需在目标库执行(MySQL 8+ 支持 IF NOT EXISTS 的写法见下;
--          若版本不支持请先 SHOW INDEX 确认再单独执行)。
--
-- ⚠️ 线上大表建索引会锁表(MySQL 8.0 的 ONLINE DDL 对普通二级索引
--    一般可并发执行,但仍建议在低峰期)。执行前先 EXPLAIN 验证收益。
-- ============================================================

-- ---------- users ----------

-- 列表查询默认按 role + status 过滤,并按 created_time 排序/区间
CREATE INDEX idx_users_role_status_created ON users (role, status, created_time);

-- 时间区间筛选(用户管理后台的「注册时间」筛选)
CREATE INDEX idx_users_created_time ON users (created_time);

-- 锁定 / 失败次数监控(管理员排查异常账号)
CREATE INDEX idx_users_locked_until ON users (account_locked_until);
CREATE INDEX idx_users_failed_attempts ON users (failed_login_attempts);

-- 软删过滤:几乎所有查询都会带 deleted_at IS NULL
CREATE INDEX idx_users_deleted_at ON users (deleted_at);

-- 注意:username / phone_number 用的是 LIKE CONCAT('%', ?, '%') 前后模糊匹配,
-- 这类条件无法走 B-tree 索引。若要支持高效模糊搜索,需改成前缀匹配
-- (LIKE 'abc%',可用索引)或引入全文索引 / Elasticsearch。
-- 但 username 本身有唯一约束,等值查询(登录路径)已经走索引,不受影响。


-- ---------- books ----------

-- 按出版日期粒度查询(GET /api/books/search/publishedDate/by)
-- 半开区间 [start, end) 扫描,并 ORDER BY published_date
CREATE INDEX idx_books_published_date ON books (published_date);

-- 库存区间筛选 + 库存排序(minStock / maxStock)
CREATE INDEX idx_books_stock ON books (stock_quantity);

-- 价格区间筛选
CREATE INDEX idx_books_price ON books (price);

-- 创建/更新时间粒度查询(GET /api/books/search/CreatedTime/by 等)
CREATE INDEX idx_books_created_time ON books (created_time);
CREATE INDEX idx_books_updated_time ON books (updated_time);

-- 注意:title / author 同样是前后模糊匹配,无法走索引(同 users 的说明)。

-- isbn 有唯一约束,等值查询与按 isbn 更新/删除已走索引,无需额外建。


-- ---------- orders ----------

-- 用户订单列表(GET /api/users/{id}/purchases),按用户过滤
CREATE INDEX idx_orders_user_id ON orders (user_id);

-- 超时关单扫描:按状态过滤 + 过期时间排序
CREATE INDEX idx_orders_status_expire ON orders (order_status, expire_time);

-- order_number 为订单业务主键,查询/更新均按它定位
CREATE UNIQUE INDEX uk_orders_order_number ON orders (order_number);

-- 订单项按订单聚合(详情页 JOIN)
CREATE INDEX idx_order_items_order_id ON order_items (order_id);
-- 按图书统计销量 / 库存回滚
CREATE INDEX idx_order_items_book_id ON order_items (book_id);
