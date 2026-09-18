-- ============================================================================
-- TMLibrary 完整建库脚本
--
-- 由 scripts/gen-full-schema.py 从 src/main/resources/db/ 下 8 份文件合并生成。
-- **不要手工编辑本文件** —— 改原始 SQL 后重新跑生成脚本。
--
-- 内容:8 张表 + 34 条分类种子(8 大类 / 26 小类)+ 演示数据
--       (5 个账号 / 12 本书 / 25 笔订单 / 33 条明细)
--
-- 用法:
--   mysql -uroot -p < scripts/full-schema.sql
--   (或在 phpMyAdmin / 宝塔的「导入」里执行)
--
-- ⚠️ 只用于**全新建库**。已存在的库上跑会因为主键冲突失败。
--    老库升级请分别执行 db/ 下的迁移脚本。
--
-- ⚠️ 演示数据(账号 / 订单)会一起插进去。生产环境如果不想要,
--    把文件末尾「演示数据」那一段整体删掉即可(不影响表结构)。
--
-- 字符集 utf8mb4 / 排序规则 utf8mb4_general_ci / 引擎 InnoDB
-- 外键:零外键(一致性由应用层 @Transactional + 行锁 + 状态守卫保证)
-- MySQL 8.0+
-- ============================================================================


CREATE DATABASE IF NOT EXISTS `tmlibrary`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE `tmlibrary`;

-- ============================================================================
-- users.sql
-- ============================================================================

-- ============================================================================
-- users — 用户
--
-- 单表建表脚本(从 scripts/schema.sql 拆出,内容与之一致)
--
-- 执行:
--   mysql -u root -p tmlibrary < src/main/resources/db/users.sql
--
-- 约定(与 schema.sql 相同):
--   - 不使用物理外键,跨表引用只建索引,引用完整性由应用层保证
--   - 引擎 InnoDB(FOR UPDATE 行锁依赖)
--   - 时间字段用 DATETIME(避开 2038 上限与时区转换)
--
-- 完整版(含库创建、初始数据说明、已有库补索引)见:scripts/schema.sql
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

    -- 只保留主查询真正用到的索引:
    --   role + status 是列表的固定过滤条件,created_time 支持区间筛选与排序
    -- 说明:列表还支持 account_locked_until / failed_login_attempts / deleted_at 等
    --       可选筛选,但这些属于低频后台排查条件,为其各建一个索引会让写放大明显
    --       而不划算 —— 未命中时走全表扫描,用户量级下可接受。
    --       若日后这些筛选变高频,再按需补索引。
    KEY `idx_users_role_status_created` (`role`, `status`, `created_time`),
    KEY `idx_users_created_time`        (`created_time`),
    -- 2026-09 新增。「忘记密码」是**未登录可调**的接口,要按 email 查用户;
    -- 没有索引就是全表扫描 —— 一个公开端点能触发全表扫,是个现成的放大攻击面。
    -- 用普通索引而非唯一索引:历史数据可能已有重复邮箱,加唯一约束会让建表失败;
    -- 真要唯一也该先清洗数据,再单独走迁移。
    KEY `idx_users_email`               (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户';

-- 已有库补索引(2026-09,忘记密码功能需要):
--   ALTER TABLE `users` ADD KEY `idx_users_email` (`email`);
--   先确认没加过:SHOW INDEX FROM `users` WHERE Key_name = 'idx_users_email';

-- 可选:若业务要求邮箱/手机号唯一,再放开下面两个约束
-- (当前注册流程未做重复预检,直接加约束会让重复注册报 409 而非友好提示)
-- ALTER TABLE `users` ADD UNIQUE KEY `uk_users_email` (`email`);
-- ALTER TABLE `users` ADD UNIQUE KEY `uk_users_phone` (`phone_number`);

-- ⚠️ username / phone_number 的模糊查询用的是 LIKE '%x%'(前后模糊),
--    无法走索引。username 的等值查询(登录)已走唯一键,不受影响。

-- ============================================================================
-- book_categories.sql
-- ============================================================================

-- ============================================================================
-- book_categories — 图书分类(两级:大类 / 小类)
--
-- 结构:
--   parent_id = 0  → 大类(顶层,如「文学小说」)
--   parent_id > 0  → 小类,指向所属大类(如「中国文学」挂在「文学小说」下)
--   book_count     → **直接挂载**在本分类下的图书数(大类恒为 0,
--                     商城展示时由后端把子类数量累加上去)
--
-- 为什么顶层用 0 而不是 NULL:
--   MySQL 的 UNIQUE 索引里 NULL 互不相等,parent_id=NULL 时
--   (NULL, '文学小说') 可以插任意多遍,唯一约束形同虚设。
--   用 0 当哨兵值,uk 才真正生效。
--
-- 执行:
--   mysql -u root -p tmlibrary < src/main/resources/db/book_categories.sql
--
-- 注意:本脚本同时负责给 books 表补 category_id 列(带 IF NOT EXISTS 判断,
--       MySQL 8.0 的 ADD COLUMN 不支持 IF NOT EXISTS,用存储过程兜底)。
-- ============================================================================


CREATE TABLE IF NOT EXISTS `book_categories` (
    -- AUTO_INCREMENT 是必须的:种子数据自己指定 id(固定值保证幂等),
    -- 但后台新建分类时应用层不传 id,没有自增就会报 "Field 'id' doesn't have a default value"。
    `id`           INT          NOT NULL AUTO_INCREMENT COMMENT '主键;种子数据用固定 id 保证幂等',
    `parent_id`    INT          NOT NULL DEFAULT 0      COMMENT '0 = 大类;>0 = 所属大类 id',
    `name`         VARCHAR(50)  NOT NULL                COMMENT '分类名',
    `icon`         VARCHAR(30)  NULL                    COMMENT 'Element Plus 图标名(只有大类有)',
    `sort_order`   INT          NOT NULL DEFAULT 0      COMMENT '排序,小的在前',
    `book_count`   INT          NOT NULL DEFAULT 0      COMMENT '直接挂载的图书数(大类恒为 0)',
    `created_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    -- 同一父下不允许重名(顶层用 0 才能让这条约束真正生效,见文件头说明)
    UNIQUE KEY `uk_book_categories_parent_name` (`parent_id`, `name`),
    KEY `idx_book_categories_parent_sort` (`parent_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='图书分类(两级)';

-- ---------------------------------------------------------------------------
-- 老库补 AUTO_INCREMENT(幂等:已是自增则跳过)
--
--   自增起点会落在种子最大值之后(804),新分类的 id 不再遵循"父 id × 100 + 序号"
--   的可读性约定 —— 建树靠的是 parent_id,不依赖 id 编码,所以没有影响。
-- ---------------------------------------------------------------------------


-- ---------------------------------------------------------------------------
-- books 补 category_id 列(幂等:列已存在则跳过)
-- ---------------------------------------------------------------------------


-- ---------------------------------------------------------------------------
-- 种子:8 大类 + 26 小类(内容与原先前端写死的 categoryTree 一一对应)
--   id 编码规则:大类 1~8;小类 = 大类id × 100 + 序号(如 101、102…)
--   INSERT IGNORE → 重复执行不报错、不新增
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `book_categories` (id, parent_id, name, icon, sort_order) VALUES
    -- 大类
    (1, 0, '文学小说', 'Reading',       1),
    (2, 0, '计算机',   'Cpu',           2),
    (3, 0, '历史人文', 'Clock',         3),
    (4, 0, '哲学思辨', 'MagicStick',    4),
    (5, 0, '艺术设计', 'PictureFilled', 5),
    (6, 0, '商业经管', 'DataAnalysis',  6),
    (7, 0, '教育考试', 'Notebook',      7),
    (8, 0, '少儿亲子', 'Star',          8),
    -- 文学小说
    (101, 1, '中国文学',   NULL, 1),
    (102, 1, '外国文学',   NULL, 2),
    (103, 1, '古典文学',   NULL, 3),
    (104, 1, '现当代文学', NULL, 4),
    -- 计算机
    (201, 2, '编程语言',       NULL, 1),
    (202, 2, '算法与数据结构', NULL, 2),
    (203, 2, 'AI / 机器学习',  NULL, 3),
    (204, 2, '系统架构',       NULL, 4),
    -- 历史人文
    (301, 3, '中国史',   NULL, 1),
    (302, 3, '世界史',   NULL, 2),
    (303, 3, '古代文明', NULL, 3),
    -- 哲学思辨
    (401, 4, '中国哲学', NULL, 1),
    (402, 4, '西方哲学', NULL, 2),
    (403, 4, '伦理学',   NULL, 3),
    -- 艺术设计
    (501, 5, '绘画',     NULL, 1),
    (502, 5, '平面设计', NULL, 2),
    (503, 5, '摄影',     NULL, 3),
    -- 商业经管
    (601, 6, '管理学',   NULL, 1),
    (602, 6, '金融投资', NULL, 2),
    (603, 6, '市场营销', NULL, 3),
    -- 教育考试
    (701, 7, '教材教辅', NULL, 1),
    (702, 7, '考试认证', NULL, 2),
    (703, 7, '语言学习', NULL, 3),
    -- 少儿亲子
    (801, 8, '绘本',     NULL, 1),
    (802, 8, '儿童文学', NULL, 2),
    (803, 8, '启蒙教育', NULL, 3);

-- ---------------------------------------------------------------------------

-- ============================================================================
-- books.sql
-- ============================================================================

-- ============================================================================
-- books — 图书
--
-- 单表建表脚本(从 scripts/schema.sql 拆出,内容与之一致)
--
-- 执行:
--   mysql -u root -p tmlibrary < src/main/resources/db/books.sql
--
-- 约定(与 schema.sql 相同):
--   - 不使用物理外键,跨表引用只建索引,引用完整性由应用层保证
--   - 引擎 InnoDB(FOR UPDATE 行锁依赖)
--   - 时间字段用 DATETIME(避开 2038 上限与时区转换)
--
-- 完整版(含库创建、初始数据说明、已有库补索引)见:scripts/schema.sql
-- ============================================================================


CREATE TABLE IF NOT EXISTS `books` (
    `id`             INT           NOT NULL AUTO_INCREMENT COMMENT '主键',
    `title`          VARCHAR(200)  NOT NULL                COMMENT '书名(校验 ≤200)',
    `author`         VARCHAR(100)  NOT NULL                COMMENT '作者(校验 ≤100)',
    `isbn`           VARCHAR(20)   NOT NULL                COMMENT 'ISBN(正则 ^[0-9Xx-]{10,20}$)',
    `price`          DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '售价',
    `published_date` DATE          NOT NULL                COMMENT '出版日期',
    `stock_quantity` INT           NOT NULL DEFAULT 0      COMMENT '库存真值;付款时原子扣减',
    -- 逻辑引用 book_categories.id(小类);NULL = 未分类。
    -- 老库升级走 db/book_categories.sql 里的 add_books_category_id 存储过程,那段是幂等的。
    `category_id`    INT           NULL                    COMMENT '所属小类 id(逻辑引用 book_categories.id,无物理外键)',
    `created_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_books_isbn` (`isbn`),

    KEY `idx_books_category`       (`category_id`),
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
-- orders.sql
-- ============================================================================

-- ============================================================================
-- orders — 订单
--
-- 单表建表脚本(从 scripts/schema.sql 拆出,内容与之一致)
--
-- 执行:
--   mysql -u root -p tmlibrary < src/main/resources/db/orders.sql
--
-- 约定(与 schema.sql 相同):
--   - 不使用物理外键,跨表引用只建索引,引用完整性由应用层保证
--   - 引擎 InnoDB(FOR UPDATE 行锁依赖)
--   - 时间字段用 DATETIME(避开 2038 上限与时区转换)
--
-- 完整版(含库创建、初始数据说明、已有库补索引)见:scripts/schema.sql
-- ============================================================================


CREATE TABLE IF NOT EXISTS `orders` (
    `id`           INT           NOT NULL AUTO_INCREMENT COMMENT '主键(内部自增,不对外暴露)',
    `order_number` BIGINT        NOT NULL                COMMENT '业务订单号(雪花算法生成,对外唯一标识)',
    `user_id`      INT           NOT NULL                COMMENT '下单用户(逻辑引用 users.id,无物理外键)',
    `total_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '订单总额(下单时快照)',
    `order_status` TINYINT       NOT NULL DEFAULT 0      COMMENT '0=PENDING 1=PAID 2=CANCELLED 3=TIMEOUT',
    `expire_time`  DATETIME      NOT NULL                COMMENT '未支付超时时间(默认下单后 30 分钟)',
    -- 支付时间:只有 PENDING → PAID 那一次流转会写,其余状态(NULL)。
    -- 不能用 updated_time 代替 —— 支付后又取消/改地址之类的操作会把 updated_time 顶掉,
    -- 「什么时候付的钱」是财务口径,必须单独落一列。
    `paid_time`    DATETIME      NULL                    COMMENT '支付时间;未支付为 NULL',
    `created_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(状态流转时自动刷新)',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_orders_order_number` (`order_number`),

    -- 用户订单列表
    KEY `idx_orders_user_id` (`user_id`),
    -- 超时关单扫描:按状态过滤 + 过期时间排序
    KEY `idx_orders_status_expire` (`order_status`, `expire_time`),
    -- 仪表盘统计:按状态过滤 + 创建时间区间扫描
    -- (WHERE order_status=1 AND created_time >= ?)
    KEY `idx_orders_status_created` (`order_status`, `created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单';

-- ---------------------------------------------------------------------------
-- 老库补 paid_time 列(幂等:列已存在则跳过)
--   历史订单没法知道真实支付时刻,用 updated_time 近似回填(只填已支付的)——
--   这些订单此后不会再改状态,所以 updated_time 基本就是支付时刻。
--   想要精确值的场景请以本列为准,并把回填视作"尽力而为"。
-- ---------------------------------------------------------------------------


-- ============================================================================
-- order_items.sql
-- ============================================================================

-- ============================================================================
-- order_items — 订单明细
--
-- 单表建表脚本(从 scripts/schema.sql 拆出,内容与之一致)
--
-- 执行:
--   mysql -u root -p tmlibrary < src/main/resources/db/order_items.sql
--
-- 约定(与 schema.sql 相同):
--   - 不使用物理外键,跨表引用只建索引,引用完整性由应用层保证
--   - 引擎 InnoDB(FOR UPDATE 行锁依赖)
--   - 时间字段用 DATETIME(避开 2038 上限与时区转换)
--
-- 完整版(含库创建、初始数据说明、已有库补索引)见:scripts/schema.sql
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
-- feedback.sql
-- ============================================================================

-- ============================================================================
-- 用户反馈系统 —— 反馈工单 + 回复/追述
--
-- 单文件可独立执行:  mysql -uroot -p tmlibrary < feedback.sql
--
-- 设计:
--   * 主表 feedbacks 存工单本身
--   * 副表 feedback_replies 一张表装两类内容:
--       - 用户的追述("我补一句")
--       - 管理员的回复 / 内部备注
--     靠 role 字段区分"是谁说的",is_internal 标记"是否对外可见"
--   * 这跟把"工单 + 评论"拆成两张表没本质区别,合一张更省 JOIN
-- ============================================================================


-- ----------------------------------------------------------------------------
-- 主表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `feedbacks` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT           COMMENT '主键',
    `user_id`       INT         NOT NULL                          COMMENT '提交人(逻辑引用 users.id)',
    `category`      VARCHAR(32) NOT NULL                          COMMENT 'BUG / FEATURE / QUESTION / OTHER',
    `title`         VARCHAR(120) NOT NULL                         COMMENT '一句话概述',
    `body`          TEXT        NOT NULL                          COMMENT '详细描述',
    `status`        TINYINT     NOT NULL DEFAULT 0                COMMENT '0=OPEN 1=IN_PROGRESS 2=RESOLVED 3=CLOSED',
    `priority`      TINYINT     NOT NULL DEFAULT 1                COMMENT '0=LOW 1=NORMAL 2=HIGH 3=URGENT',
    `created_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `resolved_time` DATETIME    NULL                              COMMENT '状态流转到 RESOLVED/CLOSED 的时刻',
    PRIMARY KEY (`id`),
    -- 用户"我的反馈"按用户 + 时间排:idx_feedbacks_user
    KEY `idx_feedbacks_user` (`user_id`, `created_time`),
    -- 管理后台"按状态筛选"按状态 + 时间排:idx_feedbacks_status
    KEY `idx_feedbacks_status` (`status`, `created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户反馈工单';

-- ----------------------------------------------------------------------------
-- 回复表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `feedback_replies` (
    `id`           BIGINT   NOT NULL AUTO_INCREMENT                COMMENT '主键',
    `feedback_id`  BIGINT   NOT NULL                              COMMENT '逻辑引用 feedbacks.id',
    `user_id`      INT      NOT NULL                              COMMENT '回复人(逻辑引用 users.id)',
    `role`         TINYINT  NOT NULL                              COMMENT '0=USER 1=ADMIN 2=BOSS — 回复人当时身份',
    `is_internal`  TINYINT  NOT NULL DEFAULT 0                   COMMENT '1=仅管理员可见的内部备注',
    `body`         TEXT     NOT NULL                              COMMENT '回复正文',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    -- 主详情页按工单 + 时间排:idx_replies_feedback
    KEY `idx_replies_feedback` (`feedback_id`, `created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='反馈回复/追述';

-- ============================================================================
-- ip_bans.sql
-- ============================================================================

-- ============================================================================
-- ip_bans — IP 封禁记录(风控)
--
-- 用途:脚本高频请求触发阈值后,把来源 IP 封禁 24 小时。
--   判定在 IpRiskControlFilter(每个 /* 请求都过),计数走 Redis,
--   封禁记录落这张表 —— 好处是 Redis 重启/淘汰后仍能追溯"封过谁、为什么"。
--
-- 执行:
--   mysql -u root -p tmlibrary < src/main/resources/db/ip_bans.sql
--
-- 运行期查询:
--   当前生效的封禁:
--     SELECT ip, reason, hit_count, banned_at, expires_at FROM ip_bans
--     WHERE unbanned_at IS NULL AND expires_at > NOW() ORDER BY banned_at DESC;
--
--   人工解封(改了 DB 还要清 Redis,否则要等 TTL):
--     UPDATE ip_bans SET unbanned_at = NOW() WHERE ip = '1.2.3.4' AND unbanned_at IS NULL;
--     redis-cli DEL tmlibrary:sec:byIp:1.2.3.4:ban
-- ============================================================================


CREATE TABLE IF NOT EXISTS `ip_bans` (
    `id`          INT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ip`          VARCHAR(45)  NOT NULL                COMMENT '客户端 IP(IPv6 最长 45 字符)',
    `reason`      VARCHAR(200) NOT NULL                COMMENT '触发原因摘要',
    `hit_count`   INT          NOT NULL DEFAULT 0      COMMENT '触发瞬间窗口内的请求数',
    `banned_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '封禁时间',
    `expires_at`  DATETIME     NOT NULL                COMMENT '封禁到期时间(默认 +24h)',
    `unbanned_at` DATETIME     NULL                    COMMENT '人工解封时间;NULL = 未解封',

    PRIMARY KEY (`id`),
    -- 封禁判定:按 ip 查是否在有效期内 → 组合索引正好覆盖
    KEY `idx_ip_bans_ip_expires` (`ip`, `expires_at`),
    -- 清理/统计:按到期时间扫过期记录
    KEY `idx_ip_bans_expires` (`expires_at`),
    -- 同一 IP 可能被多次封禁(解封后再犯),这里不建唯一键,保留历史
    KEY `idx_ip_bans_banned_at` (`banned_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='IP 封禁记录(风控)';

-- ============================================================================
-- seed-demo-data.sql
-- ============================================================================

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

-- ============================================================================
-- 给演示图书分配分类
--
-- 这些 UPDATE 原本在 book_categories.sql 里,位于 books 建表**之前**,
-- 按原顺序执行会报 Table 'books' doesn't exist —— 所以演示书的
-- category_id 一直是 NULL。这里挪到所有 INSERT 之后执行。
-- ============================================================================

-- 给演示图书分配分类(按 ISBN,只动 seed 里那 12 本;真实书不动)
-- ---------------------------------------------------------------------------
UPDATE `books` SET `category_id` = 204 WHERE `isbn` = '9787111544937'; -- 深入理解计算机系统 → 系统架构
UPDATE `books` SET `category_id` = 202 WHERE `isbn` = '9787111407010'; -- 算法导论           → 算法与数据结构
UPDATE `books` SET `category_id` = 201 WHERE `isbn` = '9787115216878'; -- 代码整洁之道       → 编程语言
UPDATE `books` SET `category_id` = 204 WHERE `isbn` = '9787111075752'; -- 设计模式           → 系统架构
UPDATE `books` SET `category_id` = 201 WHERE `isbn` = '9787115509581'; -- 重构               → 编程语言
UPDATE `books` SET `category_id` = 201 WHERE `isbn` = '9787111370048'; -- Java 并发编程实战  → 编程语言
UPDATE `books` SET `category_id` = 201 WHERE `isbn` = '9787111543246'; -- 深入理解 Java 虚拟机 → 编程语言
UPDATE `books` SET `category_id` = 102 WHERE `isbn` = '9787544253994'; -- 百年孤独           → 外国文学
UPDATE `books` SET `category_id` = 101 WHERE `isbn` = '9787506365437'; -- 活着               → 中国文学
UPDATE `books` SET `category_id` = 101 WHERE `isbn` = '9787536692930'; -- 三体               → 中国文学
UPDATE `books` SET `category_id` = 302 WHERE `isbn` = '9787508647357'; -- 人类简史           → 世界史
UPDATE `books` SET `category_id` = 101 WHERE `isbn` = '9787530216552'; -- 沉默的大多数       → 中国文学

-- ---------------------------------------------------------------------------
-- 回填 book_count —— 从 books 实表重算,不依赖上面的分配是否完整
--   (幂等:重复执行结果一致,不怕漏改哪本)
-- ---------------------------------------------------------------------------
UPDATE `book_categories` c
SET c.`book_count` = (
    SELECT COUNT(*) FROM `books` b WHERE b.`category_id` = c.`id`
)
WHERE c.`parent_id` > 0;

-- 大类恒为 0(数量由后端累加子类得出)
UPDATE `book_categories` SET `book_count` = 0 WHERE `parent_id` = 0;

-- ---------------------------------------------------------------------------
-- 自检
-- ---------------------------------------------------------------------------
SELECT c.id, c.parent_id,
       IF(c.parent_id = 0, '大类', '  小类') AS level,
       c.name, c.book_count
FROM `book_categories` c
ORDER BY c.parent_id, c.sort_order, c.id;

-- 合计:8 张表 + 36 条分类分配