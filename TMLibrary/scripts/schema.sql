-- ============================================================================
-- TMLibrary 建表脚本(MySQL 8.0+)
--
-- 使用方式:
--   mysql -u root -p < scripts/schema.sql
--   或在客户端里整段执行
--
-- 【设计约定】
-- 1. 不使用物理外键
--    orders.user_id / order_items.order_id / order_items.book_id 都是逻辑引用,
--    只建索引不建 FOREIGN KEY 约束。原因:
--      - 业务侧已通过 @Transactional + 行锁 + 状态守卫保证一致性
--      - 避免外键带来的写放大、锁竞争与删除顺序耦合
--      - 便于后续分库分表 / 归档历史订单
--    ⚠️ 代价:引用完整性由应用层负责,不存在的 user_id/book_id 不会被数据库拦截。
--
-- 2. 引擎必须是 InnoDB
--    selectOrderByOrderNumberForUpdate 依赖 SELECT ... FOR UPDATE 行锁,
--    MyISAM 不支持事务与行锁,换成 MyISAM 会让并发状态流转静默失效。
--
-- 3. 时间字段用 DATETIME(而非 TIMESTAMP)
--    TIMESTAMP 有 2038 上限,且会随会话时区转换,容易在跨时区部署时踩坑。
--    JDBC URL 已指定 serverTimezone=Asia/Shanghai。
-- ============================================================================

CREATE DATABASE IF NOT EXISTS `tmlibrary`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE `tmlibrary`;


-- ============================================================================
-- 1. users — 用户
--    实体: com.tmt.TMLibrary.entity.User
--    枚举: UserRole(0 USER / 1 ADMIN / 2 BOSS)
--          UserStatus(0 ACTIVE / 1 INACTIVE / 2 SUSPENDED)
-- ============================================================================
CREATE TABLE IF NOT EXISTS `users` (
    `id`                              INT           NOT NULL AUTO_INCREMENT COMMENT '主键',

    -- ---------- 身份 ----------
    `username`                        VARCHAR(50)   NOT NULL                COMMENT '登录名,唯一',
    `password_hash`                   VARCHAR(100)  NOT NULL                COMMENT 'BCrypt 哈希(固定 60 字符,留余量便于换算法)',
    `email`                           VARCHAR(100)  NOT NULL                COMMENT '邮箱',
    `phone_number`                    VARCHAR(20)   NOT NULL                COMMENT '手机号(注册校验长度 11)',
    `real_name`                       VARCHAR(50)   NULL                    COMMENT '真实姓名;对外返回时脱敏',
    `avatar_url`                      VARCHAR(255)  NULL                    COMMENT '头像地址',

    -- ---------- 权限与状态 ----------
    `role`                            TINYINT       NOT NULL DEFAULT 0      COMMENT '0=USER 1=ADMIN 2=BOSS;注册强制 0,仅 BOSS 可提升',
    `status`                          TINYINT       NOT NULL DEFAULT 0      COMMENT '0=ACTIVE 1=INACTIVE 2=SUSPENDED',

    -- ---------- 登录与安全 ----------
    `failed_login_attempts`           INT           NOT NULL DEFAULT 0      COMMENT '连续登录失败次数;达阈值锁定',
    `account_locked_until`            DATETIME      NULL                    COMMENT '锁定截止时间;过期后计数自动重置',
    `last_login_time`                 DATETIME      NULL                    COMMENT '最后登录时间',
    `last_login_ip`                   VARCHAR(45)   NULL                    COMMENT '最后登录 IP(IPv6 最长 45 字符)',

    -- ---------- 密码重置(预留,当前无重置流程) ----------
    `password_reset_token`            VARCHAR(64)   NULL                    COMMENT '重置令牌',
    `password_reset_token_expiration` DATETIME      NULL                    COMMENT '重置令牌过期时间',

    -- ---------- 审计 ----------
    `created_time`                    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`                    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(DB 自动维护)',
    `deleted_at`                      DATETIME      NULL                    COMMENT '软删时间;NULL=未删除',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_users_username` (`username`),

    -- 列表查询默认按 role + status 过滤,并按 created_time 排序
    KEY `idx_users_role_status_created` (`role`, `status`, `created_time`),
    KEY `idx_users_created_time`        (`created_time`),
    KEY `idx_users_locked_until`        (`account_locked_until`),
    KEY `idx_users_failed_attempts`     (`failed_login_attempts`),
    KEY `idx_users_deleted_at`          (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户';

-- 可选:若业务要求邮箱/手机号唯一,再放开下面两个约束
-- (当前注册流程未做重复预检,直接加约束会让重复注册报 409 而非友好提示)
-- ALTER TABLE `users` ADD UNIQUE KEY `uk_users_email` (`email`);
-- ALTER TABLE `users` ADD UNIQUE KEY `uk_users_phone` (`phone_number`);

-- ⚠️ username / phone_number 的模糊查询用的是 LIKE '%x%'(前后模糊),
--    无法走索引。username 的等值查询(登录)已走唯一键,不受影响。


-- ============================================================================
-- 2. books — 图书
--    实体: com.tmt.TMLibrary.entity.Book
--
--    stock_quantity 是库存真值:
--      Redis 中的 stock/reserved 只是"预占缓存",DB 才是权威。
--      下单只动 Redis,付款成功才扣减本字段(WHERE stock_quantity >= N)。
-- ============================================================================
CREATE TABLE IF NOT EXISTS `books` (
    `id`             INT           NOT NULL AUTO_INCREMENT COMMENT '主键',
    `title`          VARCHAR(200)  NOT NULL                COMMENT '书名(校验 ≤200)',
    `author`         VARCHAR(100)  NOT NULL                COMMENT '作者(校验 ≤100)',
    `isbn`           VARCHAR(20)   NOT NULL                COMMENT 'ISBN(正则 ^[0-9Xx-]{10,20}$)',
    `price`          DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '售价',
    `published_date` DATE          NOT NULL                COMMENT '出版日期',
    `stock_quantity` INT           NOT NULL DEFAULT 0      COMMENT '库存真值;付款时原子扣减',
    `created_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_books_isbn` (`isbn`),

    KEY `idx_books_published_date` (`published_date`),
    KEY `idx_books_stock`          (`stock_quantity`),
    KEY `idx_books_price`          (`price`),
    KEY `idx_books_created_time`   (`created_time`),
    KEY `idx_books_updated_time`   (`updated_time`),

    CONSTRAINT `chk_books_price_non_negative` CHECK (`price` >= 0),
    CONSTRAINT `chk_books_stock_non_negative` CHECK (`stock_quantity` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='图书';

-- ⚠️ 前后模糊的 title / author 无法走索引(同 users 的说明)。


-- ============================================================================
-- 3. orders — 订单
--    实体: com.tmt.TMLibrary.entity.Order
--    枚举: OrderStatus(0 PENDING / 1 PAID / 2 CANCELLED / 3 TIMEOUT)
-- ============================================================================
CREATE TABLE IF NOT EXISTS `orders` (
    `id`           INT           NOT NULL AUTO_INCREMENT COMMENT '主键(内部自增,不对外暴露)',
    `order_number` BIGINT        NOT NULL                COMMENT '业务订单号(雪花算法生成,对外唯一标识)',
    `user_id`      INT           NOT NULL                COMMENT '下单用户(逻辑引用 users.id,无物理外键)',
    `total_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '订单总额(下单时快照)',
    `order_status` TINYINT       NOT NULL DEFAULT 0      COMMENT '0=PENDING 1=PAID 2=CANCELLED 3=TIMEOUT',
    `expire_time`  DATETIME      NOT NULL                COMMENT '未支付超时时间(默认下单后 30 分钟)',
    `created_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(状态流转时自动刷新)',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_orders_order_number` (`order_number`),

    -- 用户订单列表
    KEY `idx_orders_user_id` (`user_id`),
    -- 超时关单扫描:按状态过滤 + 过期时间排序
    KEY `idx_orders_status_expire` (`order_status`, `expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单';


-- ============================================================================
-- 4. order_items — 订单明细
--    实体: com.tmt.TMLibrary.entity.OrderItem
--
--    price 是下单时的价格快照,不随图书调价变动。
-- ============================================================================
CREATE TABLE IF NOT EXISTS `order_items` (
    `id`           INT           NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`     INT           NOT NULL                COMMENT '所属订单(逻辑引用 orders.id,无物理外键)',
    `book_id`      INT           NOT NULL                COMMENT '图书(逻辑引用 books.id,无物理外键)',
    `quantity`     INT           NOT NULL                COMMENT '购买数量',
    `price`        DECIMAL(10,2) NOT NULL                COMMENT '下单时单价快照',
    `created_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (`id`),
    KEY `idx_order_items_order_id` (`order_id`),
    KEY `idx_order_items_book_id`  (`book_id`),

    CONSTRAINT `chk_order_items_quantity_positive` CHECK (`quantity` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单明细';


-- ============================================================================
-- 5. 初始数据
--
-- ⚠️ 注册接口强制 role=USER,而提升角色需要 BOSS 权限 —— 冷启动时没有 BOSS,
--    因此第一个管理员必须用 SQL 手动提升:
--
--    -- 1) 先用 POST /api/users/register 注册一个账号
--    -- 2) 再把它的角色改成 BOSS(2)
--    UPDATE users SET role = 2 WHERE username = 'your_admin_name';
--
-- 示例:
-- INSERT INTO books (title, author, isbn, price, published_date, stock_quantity)
-- VALUES ('深入理解 Java 虚拟机', '周志明', '9787111543246', 99.00, '2024-01-15', 100);
-- ============================================================================

-- ============================================================================
-- 6. 已有数据库的索引补建
--
-- 本脚本在建表时已包含全部索引,全新库无需额外操作。
-- 若面对的是一个"表已存在但缺索引"的旧库(本脚本的 CREATE TABLE IF NOT EXISTS
-- 不会改动已存在的表),可单独执行下面这段:
--
-- ALTER TABLE `users`
--   ADD INDEX `idx_users_role_status_created` (`role`, `status`, `created_time`),
--   ADD INDEX `idx_users_created_time` (`created_time`),
--   ADD INDEX `idx_users_locked_until` (`account_locked_until`),
--   ADD INDEX `idx_users_failed_attempts` (`failed_login_attempts`),
--   ADD INDEX `idx_users_deleted_at` (`deleted_at`);
--
-- ALTER TABLE `books`
--   ADD INDEX `idx_books_published_date` (`published_date`),
--   ADD INDEX `idx_books_stock` (`stock_quantity`),
--   ADD INDEX `idx_books_price` (`price`),
--   ADD INDEX `idx_books_created_time` (`created_time`),
--   ADD INDEX `idx_books_updated_time` (`updated_time`);
--
-- ALTER TABLE `orders`
--   ADD INDEX `idx_orders_user_id` (`user_id`),
--   ADD INDEX `idx_orders_status_expire` (`order_status`, `expire_time`);
--
-- ALTER TABLE `order_items`
--   ADD INDEX `idx_order_items_order_id` (`order_id`),
--   ADD INDEX `idx_order_items_book_id` (`book_id`);
--
-- ⚠️ 线上大表建索引会锁表,MySQL 8.0 的 ONLINE DDL 对普通二级索引一般可并发执行,
--    仍建议在低峰期操作。执行前先 EXPLAIN 验证收益。
-- ============================================================================
