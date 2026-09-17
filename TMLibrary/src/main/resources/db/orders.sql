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
DROP PROCEDURE IF EXISTS `add_orders_paid_time`;
DELIMITER $$
CREATE PROCEDURE `add_orders_paid_time`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = 'tmlibrary' AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'paid_time'
    ) THEN
        ALTER TABLE `orders`
            ADD COLUMN `paid_time` DATETIME NULL COMMENT '支付时间;未支付为 NULL' AFTER `expire_time`;

        -- 近似回填历史已支付订单
        UPDATE `orders`
        SET `paid_time` = `updated_time`
        WHERE `order_status` = 1 AND `paid_time` IS NULL;
    END IF;
END$$
DELIMITER ;

CALL `add_orders_paid_time`();
DROP PROCEDURE `add_orders_paid_time`;
