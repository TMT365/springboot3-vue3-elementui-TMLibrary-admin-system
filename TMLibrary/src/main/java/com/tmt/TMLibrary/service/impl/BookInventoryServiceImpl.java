package com.tmt.TMLibrary.service.impl;

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

    private final DefaultRedisScript<Long> preDeductScript;
    private final DefaultRedisScript<Long> releaseScript;
    private final DefaultRedisScript<Long> confirmScript;
    private final DefaultRedisScript<Long> warmUpScript;

    public BookInventoryServiceImpl(StringRedisTemplate stringRedisTemplate,
                                    BookMapper bookMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.bookMapper = bookMapper;

        this.preDeductScript = loadScript("scripts/redis/pre_deduct_stock.lua");
        this.releaseScript   = loadScript("scripts/redis/release_stock.lua");
        this.confirmScript   = loadScript("scripts/redis/confirm_stock.lua");
        this.warmUpScript    = loadScript("scripts/redis/warmup_book.lua");
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
            log.warn("release stock failed: bookId={}, qty={}, result={}", bookId, quantity, result);
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
            log.warn("confirm stock failed: bookId={}, qty={}, result={}", bookId, quantity, result);
        }
    }

    /**
     * 从 DB 读取 book 写入 Redis。
     * <br>用 HSETNX 逐字段写入，并发预热时不会被覆盖（reserved 不会被错误清零）。
     *
     * @return true=book 在 DB 中存在并已尝试预热；false=book 不存在
     */
    private boolean warmUpBook(Integer bookId) {
        Book book = bookMapper.selectById(bookId);
        if (book == null) {
            return false;
        }
        // 单次 Lua 调用完成 5 个字段的 HSETNX — 原子,不会被并发写入插入
        String bookKey = RedisKeys.bookInventory(bookId);
        stringRedisTemplate.execute(
            warmUpScript,
            List.of(bookKey),
            String.valueOf(book.getId()),
            book.getTitle() != null ? book.getTitle() : "",
            book.getPrice() != null ? book.getPrice().toPlainString() : "0",
            String.valueOf(book.getStockQuantity())
        );
        return true;
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
}
