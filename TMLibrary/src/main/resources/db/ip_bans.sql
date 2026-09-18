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

USE `tmlibrary`;

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
