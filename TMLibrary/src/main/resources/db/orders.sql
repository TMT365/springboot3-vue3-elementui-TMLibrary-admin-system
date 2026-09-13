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

USE `tmlibrary`;

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
