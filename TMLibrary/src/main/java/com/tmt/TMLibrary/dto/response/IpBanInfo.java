package com.tmt.TMLibrary.dto.response;

import com.tmt.TMLibrary.entity.IpBan;
import lombok.Data;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 封禁信息 —— 返回给前端的公告牌内容(前端用它渲染"访问已被限制"弹窗)。
 * <p>挂在 429 响应的 {@code data} 字段上。</p>
 */
@Data
public class IpBanInfo {

    /** 被封禁的 IP */
    private String ip;

    /** 触发原因摘要 */
    private String reason;

    /** 触发瞬间的请求数(用于说明"多频繁才触发") */
    private Integer hitCount;

    /** 封禁开始时间 */
    private LocalDateTime bannedAt;

    /** 解封时间 */
    private LocalDateTime expiresAt;

    /**
     * 剩余秒数 —— 前端做倒计时用。
     * 直接给数字,前端不用处理时区/解析,刷新页面也能立刻接上。
     */
    private Long remainingSeconds;

    public static IpBanInfo from(IpBan ban) {
        if (ban == null) {
            return null;
        }
        IpBanInfo info = new IpBanInfo();
        info.setIp(ban.getIp());
        info.setReason(ban.getReason());
        info.setHitCount(ban.getHitCount());
        // 截断到秒:LocalDateTime.now() 带纳秒,序列化成 "...15.986269675",
        // 而 JS 的 new Date() 只接受 3 位毫秒(Safari 会直接判 Invalid Date)
        if (ban.getBannedAt() != null) {
            info.setBannedAt(ban.getBannedAt().truncatedTo(ChronoUnit.SECONDS));
        }
        if (ban.getExpiresAt() != null) {
            info.setExpiresAt(ban.getExpiresAt().truncatedTo(ChronoUnit.SECONDS));
            long seconds = Duration.between(LocalDateTime.now(), ban.getExpiresAt()).getSeconds();
            info.setRemainingSeconds(Math.max(seconds, 0));
        }
        return info;
    }
}
