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

USE `tmlibrary`;

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
