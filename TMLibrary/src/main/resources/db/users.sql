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

USE `tmlibrary`;

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
