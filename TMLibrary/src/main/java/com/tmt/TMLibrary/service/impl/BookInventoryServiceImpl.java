package com.tmt.TMLibrary.service.impl;

import com.tmt.TMLibrary.common.metrics.InventoryMetrics;
import com.tmt.TMLibrary.common.redis.RedisKeys;
import com.tmt.TMLibrary.entity.Book;
import com.tmt.TMLibrary.mapper.BookMapper;
import com.tmt.TMLibrary.service.BookInventoryService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <h1>BookInventoryServiceImpl — Lua + HSETNX 实现</h1>
 *
 * <p>把库存原子性委托给 Redis Lua 脚本（{@code scripts/redis/*.lua}），
 * 把并发安全预热委托给 {@code HSETNX}（每个字段独立 set-if-absent）。</p>
 *
 * <h2>为什么不需要分布式锁</h2>
 *
 * <p>Redis 是单线程执行 Lua 脚本，{@code HGET → 判断 → HINCRBY} 三步在 Redis 内部不可被打断。
 * 等价于"免费的乐观锁"，不需要额外的 Redisson 或 SETNX。</p>
 *
 * <h2>为什么用 HSETNX 而不是 HSET</h2>
 *
 * <p>并发预热同一 book 时，A 和 B 都从 DB 读到 {@code stock=10}：</p>
 *
 * <ul>
 *   <li>用 {@code HSET}：B 覆盖 A 写的 {@code reserved}，丢失 A 已预占的 3 本</li>
 *   <li>用 {@code HSETNX}：B 写 {@code reserved=3} 失败（已存在），保留 A 的值 → 最终正确</li>
 * </ul>
 *
 * @see BookInventoryService
 */
@Slf4j
@Service
public class BookInventoryServiceImpl implements BookInventoryService {

    // Redis key 由 RedisKeys 统一管理

    private final StringRedisTemplate stringRedisTemplate;
    private final BookMapper bookMapper;
    private final InventoryMetrics metrics;

    private final DefaultRedisScript<Long> preDeductScript;
    private final DefaultRedisScript<Long> releaseScript;
    private final DefaultRedisScript<Long> confirmScript;
    private final DefaultRedisScript<Long> restoreScript;
    private final DefaultRedisScript<Long> warmUpScript;
    private final DefaultRedisScript<Long> syncStockScript;

    public BookInventoryServiceImpl(StringRedisTemplate stringRedisTemplate,
                                    BookMapper bookMapper,
                                    InventoryMetrics metrics) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.bookMapper = bookMapper;
        this.metrics = metrics;

        this.preDeductScript = loadScript("scripts/redis/pre_deduct_stock.lua");
        this.releaseScript   = loadScript("scripts/redis/release_stock.lua");
        this.confirmScript   = loadScript("scripts/redis/confirm_stock.lua");
        this.restoreScript   = loadScript("scripts/redis/restore_reserved.lua");
        this.warmUpScript    = loadScript("scripts/redis/warmup_book.lua");
        this.syncStockScript = loadScript("scripts/redis/sync_book_stock.lua");
    }

    @Override
    public boolean tryReserve(Integer bookId, Integer quantity) {
        if (bookId == null || quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("bookId/quantity must be non-null and positive");
        }
        String bookKey = RedisKeys.bookInventory(bookId);
        Long result = runScript(preDeductScript, bookKey, quantity);

        if (result == null) {
            return false;
        }
        if (result == -1L) {
            // book 在 Redis 中不存在 → 从 DB 预热后重试一次
            if (!warmUpBook(bookId)) {
                return false;
            }
            result = runScript(preDeductScript, bookKey, quantity);
        }
        // -1: 预热后仍不存在(bookId 非法)
        // -2: 库存不足
        // >=0: 成功，返回剩余可用库存
        return result != null && result >= 0;
    }

    @Override
    public void release(Integer bookId, Integer quantity) {
        if (bookId == null || quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("bookId/quantity must be non-null and positive");
        }
        String bookKey = RedisKeys.bookInventory(bookId);
        Long result = runScript(releaseScript, bookKey, quantity);
        if (result != null && result < 0) {
            // -1 = hash 不存在; -2 = reserved 不足(数据错位)
            // 这两种情况都会让 Redis 库存永久偏离 DB,属于需要人工/对账任务介入的异常
            log.error("INVENTORY DRIFT: release failed bookId={}, qty={}, result={} "
                + "(-1=hash missing, -2=reserved insufficient), reconcile will repair",
                bookId, quantity, result);
            metrics.driftDetected("release", bookId);
        }
    }

    @Override
    public void confirm(Integer bookId, Integer quantity) {
        if (bookId == null || quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("bookId/quantity must be non-null and positive");
        }
        String bookKey = RedisKeys.bookInventory(bookId);
        Long result = runScript(confirmScript, bookKey, quantity);
        if (result != null && result < 0) {
            log.error("INVENTORY DRIFT: confirm failed bookId={}, qty={}, result={} "
                + "(-1=hash missing, -2=reserved insufficient), reconcile will repair",
                bookId, quantity, result);
            metrics.driftDetected("confirm", bookId);
        }
    }

    /**
     * 从 DB 读取 book 写入 Redis。
     * <br>用 HSETNX 逐字段写入，并发预热时不会被覆盖（reserved 不会被错误清零）。
     *
     * <p><b>reserved 必须来自 DB</b>：Hash 缺失可能是"Redis 重启 / Key 被删"，
     * 此时 DB 里往往还有一堆 PENDING 订单占着这本书。reserved 写死 0 会把
     * 这些在途预占全忘了 —— 同一份库存能被两个用户各买一次（超卖）。
     * 所以这里查 DB 的 PENDING 实占量，再反推可用量：
     * {@code available = DB stock_quantity - reserved}（负数截断为 0），
     * 与 {@code sync_book_stock.lua} 保持同一套不变式。</p>
     *
     * @return true=book 在 DB 中存在并已尝试预热；false=book 不存在
     */
    private boolean warmUpBook(Integer bookId) {
        Book book = bookMapper.selectById(bookId);
        if (book == null) {
            return false;
        }
        // DB 里还没付款的订单占用的量 —— 就是 Redis 该记的 reserved
        Integer pendingReserved = bookMapper.selectPendingReservedQuantity(bookId);
        int reserved = pendingReserved == null ? 0 : Math.max(pendingReserved, 0);
        int dbStock = book.getStockQuantity() == null ? 0 : book.getStockQuantity();
        // available = DB 库存 - 在途预占（管理端可能把库存改到低于在途预占，截断为 0）
        int available = Math.max(dbStock - reserved, 0);
        // 单次 Lua 调用完成 5 个字段的 HSETNX — 原子,不会被并发写入插入
        String bookKey = RedisKeys.bookInventory(bookId);
        stringRedisTemplate.execute(
            warmUpScript,
            List.of(bookKey),
            String.valueOf(book.getId()),
            book.getTitle() != null ? book.getTitle() : "",
            book.getPrice() != null ? book.getPrice().toPlainString() : "0",
            String.valueOf(available),
            String.valueOf(reserved)
        );
        return true;
    }

    /**
     * 归还一笔已被 confirm 的预占（{@link #confirm} 的逆操作）。
     *
     * <p>只在"DB 事务回滚、但 confirm 已经执行"的补偿钩子里用到：
     * confirm 把 {@code reserved} 减掉了，事务一回滚这笔预占就得还回来，
     * 否则 Redis 可用量会比真实值少，别的用户会看到"库存不足"。</p>
     *
     * <p>只加 {@code reserved}，不动 {@code stock} —— confirm 也没动过 stock
     * （预占时就减过一次），所以撤销 confirm 同样不需要碰 stock。</p>
     *
     * @param bookId   图书主键
     * @param quantity 要归还的数量（必须 {@code > 0}）
     */
    @Override
    public void restoreReserved(Integer bookId, Integer quantity) {
        if (bookId == null || quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("bookId/quantity must be non-null and positive");
        }
        String bookKey = RedisKeys.bookInventory(bookId);
        Long result = runScript(restoreScript, bookKey, quantity);
        if (result != null && result < 0) {
            log.error("INVENTORY DRIFT: restoreReserved failed bookId={}, qty={}, result={} "
                + "(-1=hash missing), reconcile will repair",
                bookId, quantity, result);
            metrics.driftDetected("restoreReserved", bookId);
        }
    }

    private Long runScript(DefaultRedisScript<Long> script, String key, Integer qty) {
        return stringRedisTemplate.execute(script, List.of(key), String.valueOf(qty));
    }

    private static DefaultRedisScript<Long> loadScript(String classpathPath) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(classpathPath));
        script.setResultType(Long.class);
        return script;
    }

    @Override
    public void syncStockFromDb(Integer bookId) {
        if (bookId == null) {
            return;
        }
        Book book = bookMapper.selectById(bookId);
        if (book == null || book.getStockQuantity() == null) {
            return;
        }
        // 保留 reserved,反推 stock;hash 不存在时脚本直接返回 0(下次预热会读 DB)
        stringRedisTemplate.execute(
            syncStockScript,
            List.of(RedisKeys.bookInventory(bookId)),
            String.valueOf(book.getStockQuantity())
        );
    }

    @Override
    public boolean reconcile(Integer bookId) {
        if (bookId == null) {
            return false;
        }
        String bookKey = RedisKeys.bookInventory(bookId);

        List<Object> values = stringRedisTemplate.opsForHash().multiGet(bookKey, List.of("stock", "reserved"));
        if (values == null || values.isEmpty() || values.get(0) == null) {
            // hash 不存在 或 没有 stock 字段 — 交给 warmUpBook 处理
            return false;
        }

        long redisStock = parseLong(values.get(0), 0L);
        long redisReserved = values.size() > 1 ? parseLong(values.get(1), 0L) : 0L;

        Book book = bookMapper.selectById(bookId);
        if (book == null || book.getStockQuantity() == null) {
            return false;
        }
        long dbStock = book.getStockQuantity();

        if (redisStock + redisReserved == dbStock) {
            return false;   // 不变式成立,无需处理
        }

        log.error("INVENTORY DRIFT detected: bookId={}, redis stock={}, redis reserved={}, "
                + "sum={}, db stock={} — repairing (stock := db - reserved)",
            bookId, redisStock, redisReserved, redisStock + redisReserved, dbStock);

        stringRedisTemplate.execute(
            syncStockScript,
            List.of(bookKey),
            String.valueOf(dbStock)
        );
        metrics.reconcileRepaired(bookId);
        return true;
    }

    private static long parseLong(Object v, long fallback) {
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
