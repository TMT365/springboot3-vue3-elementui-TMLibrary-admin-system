-- ============================================================================
-- 迁移:orders 表增加「支付方式」列
--
-- 背景:原来 payOrder 收了 paymentMethod 参数但直接丢掉(源码注释写着"预留字段,
-- 接网关时验签后再调本方法"),订单表也没有对应列 —— 用户选了什么支付方式,
-- 系统完全没记录。这次把前端支付弹窗(微信/支付宝/QQ)接上,顺带把它落库。
--
-- 给已有环境用。全新环境不需要执行 —— db/orders.sql 里已经带上这一列了。
--
-- 执行:
--   mysql -u<user> -p <db> < V2__orders_payment_method.sql
--
-- 幂等性:MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS,重复执行会报
-- "Duplicate column name 'payment_method'"(1060)。看到这个错就说明已经加过了,
-- 可以忽略。想更稳就先跑下面那条 SELECT 确认。
-- ============================================================================

-- 先确认当前状态(返回 0 行 = 还没加,可以执行下面的 ALTER):
--   SELECT COLUMN_NAME FROM information_schema.COLUMNS
--   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders'
--     AND COLUMN_NAME = 'payment_method';

ALTER TABLE `orders`
    ADD COLUMN `payment_method` VARCHAR(16) NULL
        COMMENT '支付方式:WECHAT/ALIPAY/QQ;未支付为 NULL'
        AFTER `paid_time`;

-- ----------------------------------------------------------------------------
-- 存量数据的处理:全部留 NULL
--
-- 迁移前已支付的订单**无法回填** —— 当时压根没记录用户选了什么,
-- 任何猜测都是编数据。查历史订单时会看到 payment_method 为 NULL,
-- 这是正确的事实表达,不是数据缺失。
-- 前端展示时会把它渲染成「-」而不是空白,见 UserOrders.vue。
-- ----------------------------------------------------------------------------
