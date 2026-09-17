package com.tmt.TMLibrary.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * IP 封禁记录 —— 对应 ip_bans 表。
 * <p>同一 IP 可以有多次封禁(解封后再犯),表里不做唯一约束,保留历史。</p>
 */
@Data
public class IpBan {

    private Integer id;

    /** 客户端 IP(IPv6 最长 45 字符) */
    private String ip;

    /** 触发原因摘要,例如 "60 秒内请求 312 次,超过阈值 150" */
    private String reason;

    /** 触发瞬间窗口内的请求数 */
    private Integer hitCount;

    private LocalDateTime bannedAt;

    /** 封禁到期时间(默认 bannedAt + 24h) */
    private LocalDateTime expiresAt;

    /** 人工解封时间;null = 未解封 */
    private LocalDateTime unbannedAt;
}
