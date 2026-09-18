package com.tmt.TMLibrary.service;

import com.tmt.TMLibrary.dto.response.IpBanInfo;
import com.tmt.TMLibrary.entity.IpBan;

import java.util.List;

/**
 * IP 风控 —— 高频请求检测 + 封禁 24 小时。
 *
 * <h2>判定链路</h2>
 * <pre>
 *   每个 /* 请求
 *     → Redis 查封禁标记(1 次 GET,不碰 DB)
 *       命中 → 429 + 封禁详情
 *       未命中 → Redis 计数 +1(固定窗口)
 *          超过阈值 → 落库 ip_bans + 写 Redis 标记(TTL = 剩余封禁时长) → 429
 * </pre>
 *
 * <p><b>Redis 与 DB 的分工</b>:Redis 是每次请求的判定源(快),DB 是审计记录
 * (Redis 被清空/重启后仍能查"封过谁、为什么")。代价是 Redis 被清空会提前解封,
 * 需要人工重新封 —— 见 db/ip_bans.sql 里的运维说明。</p>
 */
public interface IpBanService {

    /** 该 IP 是否处于封禁中;未封禁返回 {@code null} */
    IpBanInfo getActiveBan(String ip);

    /**
     * 记一次请求,并判断是否触发封禁。
     *
     * @return 本次触发封禁时返回封禁详情;未触发返回 {@code null}
     */
    IpBanInfo recordAndDetect(String ip);

    /** 人工解封:标记 DB 记录 + 删 Redis 标记 */
    void unban(String ip);

    /** 生效中的封禁列表(管理端查看用) */
    List<IpBan> listActiveBans();
}
