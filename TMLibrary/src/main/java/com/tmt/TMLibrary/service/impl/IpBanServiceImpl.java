package com.tmt.TMLibrary.service.impl;

import com.tmt.TMLibrary.common.redis.RedisKeys;
import com.tmt.TMLibrary.dto.response.IpBanInfo;
import com.tmt.TMLibrary.entity.IpBan;
import com.tmt.TMLibrary.mapper.IpBanMapper;
import com.tmt.TMLibrary.service.IpBanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * IP 风控实现 —— 阈值策略见 application.yml 的 app.security.ip-ban.*
 *
 * <p>计数用<b>固定窗口</b>(window = epochSeconds / windowSeconds):
 * 比滑动窗口省一半内存,代价是窗口边界上最多可能放过 2 倍流量 ——
 * 对"封脚本"这个场景完全够用(脚本是持续高频,不是卡边界打)。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IpBanServiceImpl implements IpBanService {

    private final IpBanMapper ipBanMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /** 总开关 —— 压测/本地调试可关掉 */
    @Value("${app.security.ip-ban.enabled:true}")
    private boolean enabled;

    /** 单个窗口内允许的最大请求数,超过即封禁 */
    @Value("${app.security.ip-ban.max-requests:150}")
    private int maxRequests;

    /** 窗口长度(秒) */
    @Value("${app.security.ip-ban.window-seconds:60}")
    private int windowSeconds;

    /** 封禁时长(小时) */
    @Value("${app.security.ip-ban.ban-hours:24}")
    private int banHours;

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public IpBanInfo getActiveBan(String ip) {
        if (!enabled || ip == null) {
            return null;
        }
        try {
            String json = stringRedisTemplate.opsForValue().get(RedisKeys.ipBan(ip));
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, IpBanInfo.class);
        } catch (Exception e) {
            // 读缓存失败不能拦住正常用户 —— 放行,由下一次请求重试
            log.warn("读取 IP 封禁标记失败,按未封禁处理: ip={}, err={}", ip, e.getMessage());
            return null;
        }
    }

    @Override
    public IpBanInfo recordAndDetect(String ip) {
        if (!enabled || ip == null) {
            return null;
        }
        try {
            long window = System.currentTimeMillis() / 1000 / windowSeconds;
            String key = RedisKeys.ipRate(ip, window);

            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count == null) {
                return null;
            }
            if (count == 1L) {
                // 窗口 key 的 TTL 给 2 倍窗口:固定窗口在边界上跨两格,留余量防止提前过期
                stringRedisTemplate.expire(key, Duration.ofSeconds(windowSeconds * 2L));
            }
            if (count <= maxRequests) {
                return null;
            }

            // 超阈值 → 封禁
            String reason = String.format("%d 秒内请求 %d 次,超过阈值 %d 次",
                    windowSeconds, count, maxRequests);
            IpBanInfo info = ban(ip, reason, count.intValue());
            log.warn("IP 触发风控被封禁: ip={}, reason={}, 封禁 {} 小时", ip, reason, banHours);
            return info;
        } catch (Exception e) {
            // 风控本身故障不能拖垮业务:记日志后放行
            log.error("IP 风控计数失败,放行本次请求: ip={}", ip, e);
            return null;
        }
    }

    @Override
    public void unban(String ip) {
        int rows = ipBanMapper.unban(ip);
        stringRedisTemplate.delete(RedisKeys.ipBan(ip));

        // 计数器必须一起清 —— 否则解封后第一个请求就在"已超阈值"的计数上 +1,
        // 又立刻重新封禁(实测过:只删封禁标记的话解封无效)。
        // 删当前 + 上一个窗口:固定窗口跨边界时两个 key 都可能存在。
        long window = System.currentTimeMillis() / 1000 / windowSeconds;
        stringRedisTemplate.delete(RedisKeys.ipRate(ip, window));
        stringRedisTemplate.delete(RedisKeys.ipRate(ip, window - 1));

        log.info("人工解封 IP: ip={}, 影响记录 {} 条,已重置请求计数", ip, rows);
    }

    @Override
    public List<IpBan> listActiveBans() {
        return ipBanMapper.selectActiveBans();
    }

    /** 落库 + 写 Redis 标记(TTL = 封禁时长 → 到期自动失效,不需要定时任务解封) */
    private IpBanInfo ban(String ip, String reason, int hitCount) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusHours(banHours);

        IpBan entity = new IpBan();
        entity.setIp(ip);
        entity.setReason(reason);
        entity.setHitCount(hitCount);
        entity.setBannedAt(now);
        entity.setExpiresAt(expiresAt);
        ipBanMapper.insertBan(entity);

        // 写 Redis 的内容就是前端要看的 IpBanInfo(含 remainingSeconds)
        IpBanInfo info = IpBanInfo.from(entity);
        try {
            stringRedisTemplate.opsForValue().set(
                    RedisKeys.ipBan(ip),
                    objectMapper.writeValueAsString(info),
                    Expiration.from(banHours, TimeUnit.HOURS));
        } catch (Exception e) {
            // 落库成功了但 Redis 没写进去 → 这次封禁不生效,记录告警
            log.error("封禁已落库但写 Redis 失败,封禁暂不生效: ip={}", ip, e);
        }
        return info;
    }
}
