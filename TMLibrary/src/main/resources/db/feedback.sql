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

USE `tmlibrary`;

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
