package com.tmt.TMLibrary.scheduler;

import com.tmt.TMLibrary.common.redis.RedisKeys;
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

    // Redis key 由 RedisKeys 统一管理(SCHED_LOCK_ORDER_EXPIRE / USER_PENDING_EXPIRE_IDX_PATTERN)

    /**
     * 扫描间隔(秒)— 用 fixedDelay 语义:上一次扫完后再等这么久,而不是固定速率。
     * <p>fixedRate 的问题是:若某次扫描耗时 &gt; 周期,锁 TTL 到期后其它实例会并发进入。</p>
     */
    private static final long SCAN_INTERVAL_MS = 60_000;

    /**
     * 分布式锁 TTL(秒)— 必须 &gt; 单次扫描的最坏耗时。
     * <p>配合 fixedDelay:本实例的锁只可能被其它实例抢;TTL 留足余量避免扫描中被抢占。
     * 即使被抢占也不会写坏数据 — cancelExpiredOrder 有 FOR UPDATE + 状态守卫 UPDATE 双重保护。</p>
     */
    private static final long LOCK_TTL_SECONDS = 120;

    /** 单批从 ZSet 取多少条(防止一次性拉取过多阻塞 Redis) */
    private static final int ZSET_BATCH_SIZE = 200;

    /** 单个 idx 每轮最多处理多少批 — 防止持续失败的订单把扫描卡死 */
    private static final int MAX_BATCHES_PER_IDX = 50;

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

    @Scheduled(fixedDelay = SCAN_INTERVAL_MS, initialDelay = 30_000)
    public void scanExpiredOrders() {
        String lockValue = UUID.randomUUID().toString();

        // 1) 抢锁：SETNX + TTL
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(
            RedisKeys.SCHED_LOCK_ORDER_EXPIRE,
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
            stringRedisTemplate.execute(unlockScript, List.of(RedisKeys.SCHED_LOCK_ORDER_EXPIRE), lockValue);
        }
    }

    private void doScan() {
        long nowMillis = System.currentTimeMillis();
        ScanOptions options = ScanOptions.scanOptions()
            .match(RedisKeys.USER_PENDING_EXPIRE_IDX_PATTERN)
            .count(100)
            .build();

        try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String idxKey = cursor.next();
                processOneIndex(idxKey, nowMillis);
            }
        }
    }

    /**
     * 处理单个用户的 pending:expire idx — 分批拉取,避免一次性 ZRANGEBYSCORE 拉出十万级 member 阻塞 Redis。
     *
     * <p>cancelExpiredOrder 成功后会把成员从 ZSet 移除(afterCommit),因此本方法用 offset=0
     * 反复取"最旧的一批",直到取空或达到 {@link #MAX_BATCHES_PER_IDX} 上限。
     * 持续失败的订单会留在 ZSet 里,靠批次数上限兜底防止死循环。</p>
     */
    private void processOneIndex(String idxKey, long nowMillis) {
        for (int batch = 0; batch < MAX_BATCHES_PER_IDX; batch++) {
            Set<String> expired = stringRedisTemplate.opsForZSet()
                .rangeByScore(idxKey, 0, nowMillis, 0, ZSET_BATCH_SIZE);
            if (expired == null || expired.isEmpty()) {
                return;
            }

            for (String orderNumberStr : expired) {
                try {
                    purchaseService.cancelExpiredOrder(Long.parseLong(orderNumberStr));
                } catch (NumberFormatException e) {
                    // 脏数据 — 直接踢出 idx,否则每轮都会重新扫到
                    log.warn("[order-expire] non-numeric member {}, removing from idx={}",
                        orderNumberStr, idxKey);
                    stringRedisTemplate.opsForZSet().remove(idxKey, orderNumberStr);
                } catch (Exception e) {
                    // 单笔失败不影响其他订单;该成员保留在 ZSet,下轮重试
                    log.warn("[order-expire] failed to cancel order {}: {}",
                        orderNumberStr, e.getMessage());
                }
            }
        }
        log.warn("[order-expire] idx {} hit batch limit ({}), remaining entries deferred to next run",
            idxKey, MAX_BATCHES_PER_IDX);
    }
}
