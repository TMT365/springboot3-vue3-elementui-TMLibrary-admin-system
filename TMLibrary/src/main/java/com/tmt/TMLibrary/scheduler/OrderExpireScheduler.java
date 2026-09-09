package com.tmt.TMLibrary.scheduler;

import com.tmt.TMLibrary.service.PurchaseService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * <h1>OrderExpireScheduler — 超时订单自动关单</h1>
 *
 * <p>每 60 秒扫描一次 {@code pendingExpireIdx} ZSet，对所有 {@code score ≤ now} 的订单号调
 * {@code cancelExpiredOrder}。</p>
 *
 * <h2>并发安全</h2>
 *
 * <p>用 Redis SETNX 实现分布式锁：</p>
 *
 * <ul>
 *   <li><strong>持有者</strong>：拿到 {@code tmlibrary:scheduler:lock:order-expire} 的实例</li>
 *   <li><strong>TTL</strong>：50s（必须 {@code <} &#64;Scheduled 周期 60s，否则下个周期触发时锁未过期）</li>
 *   <li><strong>释放</strong>：Lua 脚本 {@code if get == self then del}（避免误删别人的锁）</li>
 * </ul>
 *
 * <h2>扫描流程</h2>
 *
 * <pre>
 * 1. SCAN 匹配 tmlibrary:user:*:orders:pending:expire:idx
 * 2. 对每个 idx ZRANGEBYSCORE 0 nowMillis
 * 3. 逐个调 cancelExpiredOrder(orderNumber)
 * 4. Lua 释放锁
 * </pre>
 *
 * <h2>为什么不用 MQ 延迟消息</h2>
 *
 * <p>单一任务、单实例场景下，{@code @Scheduled + Redis 锁} 比 MQ 简单一个数量级。
 * 任务 ≥ 5 个或需要分片路由时再升级到 XXL-Job。</p>
 *
 * @see PurchaseService#cancelExpiredOrder(Long)
 */
@Slf4j
@Component
public class OrderExpireScheduler {

    private static final String SCHEDULER_LOCK_KEY = "tmlibrary:scheduler:lock:order-expire";
    private static final long LOCK_TTL_SECONDS = 50;  // 必须 &lt; @Scheduled fixedRate
    private static final String PENDING_EXPIRE_IDX_PATTERN = "tmlibrary:user:*:orders:pending:expire:idx";

    // 经典 Redis 分布式锁释放模式：check-then-del，原子
    private static final String UNLOCK_LUA =
        "if redis.call('get', KEYS[1]) == ARGV[1] then " +
        "  return redis.call('del', KEYS[1]) " +
        "else " +
        "  return 0 " +
        "end";

    private final StringRedisTemplate stringRedisTemplate;
    private final PurchaseService purchaseService;
    private final DefaultRedisScript<Long> unlockScript;

    public OrderExpireScheduler(StringRedisTemplate stringRedisTemplate,
                                PurchaseService purchaseService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.purchaseService = purchaseService;
        this.unlockScript = new DefaultRedisScript<>(UNLOCK_LUA, Long.class);
    }

    @Scheduled(fixedRate = 60_000, initialDelay = 30_000)
    public void scanExpiredOrders() {
        String lockValue = UUID.randomUUID().toString();

        // 1) 抢锁：SETNX + TTL
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(
            SCHEDULER_LOCK_KEY,
            lockValue,
            Duration.ofSeconds(LOCK_TTL_SECONDS)
        );
        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("[order-expire] another instance holds the lock, skipping");
            return;
        }

        try {
            doScan();
        } catch (Exception e) {
            log.error("[order-expire] scan failed", e);
        } finally {
            // 2) 释放锁：Lua 保证只有自己持锁时才删
            stringRedisTemplate.execute(unlockScript, List.of(SCHEDULER_LOCK_KEY), lockValue);
        }
    }

    private void doScan() {
        long nowMillis = System.currentTimeMillis();
        ScanOptions options = ScanOptions.scanOptions()
            .match(PENDING_EXPIRE_IDX_PATTERN)
            .count(100)
            .build();

        try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String idxKey = cursor.next();
                // ZRANGEBYSCORE key 0 now  — 拿所有 score <= now 的 member（即已过期订单号）
                Set<String> expired = stringRedisTemplate.opsForZSet().rangeByScore(idxKey, 0, nowMillis);
                if (expired == null || expired.isEmpty()) continue;

                for (String orderNumberStr : expired) {
                    try {
                        purchaseService.cancelExpiredOrder(Long.parseLong(orderNumberStr));
                    } catch (NumberFormatException e) {
                        log.warn("[order-expire] non-numeric member in idx: {}", orderNumberStr);
                    } catch (Exception e) {
                        // 单笔失败不影响其他订单
                        log.warn("[order-expire] failed to cancel order {}: {}",
                            orderNumberStr, e.getMessage());
                    }
                }
            }
        }
    }
}
