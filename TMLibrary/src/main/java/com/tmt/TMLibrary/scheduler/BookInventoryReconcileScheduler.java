package com.tmt.TMLibrary.scheduler;

import com.tmt.TMLibrary.common.redis.RedisKeys;
import com.tmt.TMLibrary.service.BookInventoryService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;

/**
 * <h1>BookInventoryReconcileScheduler — 库存对账</h1>
 *
 * <p>Redis 的库存是"预占缓存",DB 的 {@code books.stock_quantity} 才是真值。
 * 两者由不同的代码路径维护(下单走 Lua、付款走条件 UPDATE、取消走 Lua),
 * 任何一处异常都可能让 Redis 侧永久偏离真值 —— 例如:</p>
 *
 * <ul>
 *   <li>{@code release} 返回 -2(reserved 不足),只记日志不修正</li>
 *   <li>重复 {@code confirm}、人工误操作 Redis</li>
 *   <li>历史上"删除 Hash 后重建"留下的错位</li>
 * </ul>
 *
 * <p>本任务周期性地用不变式校验并修复:</p>
 *
 * <pre>
 *   Redis(stock + reserved) == DB.stock_quantity
 * </pre>
 *
 * <p>验证:
 * <ul>
 *   <li>初始 10 可用 → {@code 10 + 0 == 10} ✓</li>
 *   <li>预占 3 → {@code 7 + 3 == 10} ✓</li>
 *   <li>付款 → DB 扣成 7,{@code 7 + 0 == 7} ✓</li>
 *   <li>取消 → {@code 10 + 0 == 10} ✓</li>
 * </ul>
 *
 * <p>不成立时以 DB 为准修正 {@code stock}(保留 {@code reserved}),由
 * {@link BookInventoryService#reconcile(Integer)} 执行。</p>
 *
 * <h2>并发安全</h2>
 *
 * <p>与 {@link OrderExpireScheduler} 同款 Redis SETNX 锁 + Lua 释放,
 * 保证多实例部署时只有一个节点在对账。</p>
 *
 * @see BookInventoryService#reconcile(Integer)
 */
@Slf4j
@Component
public class BookInventoryReconcileScheduler {

    /** 对账周期 — 库存漂移不是紧急故障,10 分钟一次足够,避免给 Redis 添压力 */
    private static final long RECONCILE_INTERVAL_MS = 10 * 60 * 1000L;

    /** 首次延迟 2 分钟,避开应用启动时的预热高峰 */
    private static final long INITIAL_DELAY_MS = 2 * 60 * 1000L;

    /** 锁 TTL — 留足余量,扫描大库时也不至于被其他实例抢占 */
    private static final long LOCK_TTL_SECONDS = 300;

    /** 单次对账最多处理多少个 book — 防止 key 极多时单轮跑太久 */
    private static final int MAX_BOOKS_PER_RUN = 5000;

    private static final String UNLOCK_LUA =
        "if redis.call('get', KEYS[1]) == ARGV[1] then " +
        "  return redis.call('del', KEYS[1]) " +
        "else " +
        "  return 0 " +
        "end";

    private final StringRedisTemplate stringRedisTemplate;
    private final BookInventoryService bookInventoryService;
    private final DefaultRedisScript<Long> unlockScript;

    public BookInventoryReconcileScheduler(StringRedisTemplate stringRedisTemplate,
                                           BookInventoryService bookInventoryService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.bookInventoryService = bookInventoryService;
        this.unlockScript = new DefaultRedisScript<>(UNLOCK_LUA, Long.class);
    }

    @Scheduled(fixedDelay = RECONCILE_INTERVAL_MS, initialDelay = INITIAL_DELAY_MS)
    public void reconcileAll() {
        String lockValue = UUID.randomUUID().toString();
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(
            RedisKeys.SCHED_LOCK_INVENTORY_RECONCILE,
            lockValue,
            Duration.ofSeconds(LOCK_TTL_SECONDS)
        );
        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("[inventory-reconcile] another instance holds the lock, skipping");
            return;
        }

        try {
            doReconcile();
        } catch (Exception e) {
            log.error("[inventory-reconcile] reconcile failed", e);
        } finally {
            stringRedisTemplate.execute(
                unlockScript, List.of(RedisKeys.SCHED_LOCK_INVENTORY_RECONCILE), lockValue);
        }
    }

    private void doReconcile() {
        ScanOptions options = ScanOptions.scanOptions()
            .match(RedisKeys.BOOK_INVENTORY_PATTERN)
            .count(200)
            .build();

        int scanned = 0;
        int repaired = 0;

        try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
            while (cursor.hasNext() && scanned < MAX_BOOKS_PER_RUN) {
                String key = cursor.next();
                scanned++;

                Matcher m = RedisKeys.BOOK_INVENTORY_ID_EXTRACTOR.matcher(key);
                if (!m.matches()) {
                    log.warn("[inventory-reconcile] unexpected key shape, skipping: {}", key);
                    continue;
                }

                int bookId;
                try {
                    bookId = Integer.parseInt(m.group(1));
                } catch (NumberFormatException e) {
                    log.warn("[inventory-reconcile] non-numeric bookId in key: {}", key);
                    continue;
                }

                try {
                    if (bookInventoryService.reconcile(bookId)) {
                        repaired++;
                    }
                } catch (Exception e) {
                    // 单本书失败不影响其余书籍
                    log.warn("[inventory-reconcile] failed to reconcile bookId={}: {}", bookId, e.getMessage());
                }
            }
        }

        if (repaired > 0) {
            log.warn("[inventory-reconcile] scanned={}, repaired={} — 出现漂移说明有异常路径,"
                + "请检查 INVENTORY DRIFT 相关日志定位根因", scanned, repaired);
        } else {
            log.info("[inventory-reconcile] scanned={}, all consistent", scanned);
        }
    }
}
