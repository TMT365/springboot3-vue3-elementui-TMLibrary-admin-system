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

USE `tmlibrary`;

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
