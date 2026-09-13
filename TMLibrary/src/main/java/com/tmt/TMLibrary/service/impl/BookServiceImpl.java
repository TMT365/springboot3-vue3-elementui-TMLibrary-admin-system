package com.tmt.TMLibrary.service.impl;

import com.tmt.TMLibrary.common.redis.RedisKeys;
import com.tmt.TMLibrary.dto.request.BookSearchRequest;
import com.tmt.TMLibrary.dto.request.BookPublishedDateByRequest;
import com.tmt.TMLibrary.dto.request.BookDateTimeByRequest;
import com.tmt.TMLibrary.dto.request.BookUpdateRequest;
import com.tmt.TMLibrary.dto.request.BookSaveRequest;
import com.tmt.TMLibrary.service.BookService;

import com.tmt.TMLibrary.entity.Book;
import com.tmt.TMLibrary.exception.BusinessException;
import com.tmt.TMLibrary.common.Result.PageResult;
import com.tmt.TMLibrary.common.Result.ResultCode;
import com.tmt.TMLibrary.mapper.BookMapper;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import java.time.LocalDateTime;
import java.time.LocalDate;

import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import com.tmt.TMLibrary.common.utils.RandomExpirationTimeWithOffset;



@Service
public class BookServiceImpl implements BookService {

    private final BookMapper bookMapper;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    // Redis key 由 RedisKeys 统一管理
    // 旧常量(已删除,域名错配):
    //   REDIS_BOOKS_INFO_PATH = "tmlibrary:user:books:"   ← 错放在 user 域,现改为 "book:isbn:" (RedisKeys.BOOK_INFO_BY_ISBN)

    /**
     * SingleFlight 飞行中查询表 — 防止同一 isbn 的并发请求全部穿透到 DB。
     * <p>key = isbn,value = 该 isbn 当前正在 DB 查询的 CompletableFuture。其他线程进入时 {@link #getByISBN(String)}
     * 看到 inflight 里有自己这个 isbn,直接 {@code .get()} 复用结果,不重复打 DB。</p>
     * <p>比分布式锁方案轻量 — 仅限单 JVM 进程内;多实例部署需换 Redis SETNX。</p>
     */
    private final ConcurrentHashMap<String, CompletableFuture<Book>> inflight = new ConcurrentHashMap<>();

    /** 负缓存占位 JSON — 用 sentinel 而不是空串,避免 readValue("") 抛异常 */
    private static final String NEGATIVE_SENTINEL = "{\"__sentinel__\":true}";

    public BookServiceImpl(BookMapper bookMapper, StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.bookMapper = bookMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public PageResult<Book> page(int page, int size) {
        // 不走Redis，直接查询 MySql
        int offset = (page - 1) * size;
        int total = bookMapper.countBooks();
        List<Book> books = bookMapper.selectList(offset, size);
        return new PageResult<>(total, books);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(BookSaveRequest request) {
        Book book = new Book();
        BeanUtils.copyProperties(request, book);
        book.setCreatedTime(LocalDateTime.now());
        bookMapper.insertBook(book);

        // 主动失效负缓存 — 之前如果有同 isbn 的负缓存,必须清掉,否则新书3 分钟内看不到
        stringRedisTemplate.delete(RedisKeys.bookInfoByIsbn(request.getIsbn()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByISBN(String isbn) {
        Book book = bookMapper.selectBookByISBN(isbn);
        if (book == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "图书不存在, isbn=" + isbn);
        }
        int rowsAffected = bookMapper.deleteBookByISBN(isbn);
        if (rowsAffected > 0) {
            // 失效图书详情缓存 + 库存 Hash(同一 isbn 的 bookId 需要回查)
            stringRedisTemplate.delete(RedisKeys.bookInfoByIsbn(isbn));
            stringRedisTemplate.delete(RedisKeys.bookInventory(book.getId()));
        }
        return rowsAffected;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByISBN(String isbn, BookUpdateRequest request) {
        Book book = bookMapper.selectBookByISBN(isbn);
        if (book == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "图书不存在, isbn=" + isbn);
        }
        BeanUtils.copyProperties(request, book);
        book.setUpdatedTime(LocalDateTime.now());
        int rowsAffected = bookMapper.updateBookByISBN(book);
        if (rowsAffected > 0) {
            // 失效两条缓存路径:
            // 1) book:isbn:{isbn} — BookService 自己的详情缓存
            // 2) book:byId:{id}:inventory — BookInventoryService 的库存 Hash(含 stock 字段)
            stringRedisTemplate.delete(RedisKeys.bookInfoByIsbn(isbn));
            stringRedisTemplate.delete(RedisKeys.bookInventory(book.getId()));
        }
        return rowsAffected;
    }

    /**
     * 按 ISBN 查询 — Cache-Aside + SingleFlight 防穿透/击穿/雪崩。
     *
     * <h2>防止的问题</h2>
     * <ul>
     *   <li><strong>缓存穿透</strong>:同一 isbn 大量请求 → 缓存命中负 sentinel → 跳过 DB,直到 sentinel 过期</li>
     *   <li><strong>缓存击穿</strong>:同一 isbn 缓存同时过期 → 多个请求并发打 DB → SingleFlight 让 leader 独占 DB,follower wait</li>
     *   <li><strong>缓存雪崩</strong>:用 {@code RandomExpirationTimeWithOffset} 给 TTL 加随机偏移,避免大量 key 同时过期</li>
     * </ul>
     *
     * <h2>负缓存策略</h2>
     * 写入 sentinel JSON(非空串),避免 {@code readValue("")} 抛异常;{@code create} 时主动 delete 失效。
     */
    @Override
    public Book getByISBN(String isbn) {
        String key = RedisKeys.bookInfoByIsbn(isbn);

        // Step 1: 读缓存(命中 → 直接返回)
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached != null) {
            if (NEGATIVE_SENTINEL.equals(cached)) {
                throw new BusinessException(ResultCode.NOT_FOUND, "图书不存在, isbn=" + isbn);
            }
            try {
                return objectMapper.readValue(cached, Book.class);
            } catch (Exception e) {
                // 损坏的 cache,清掉走 DB
                log.warn("corrupt cache for isbn={}, refetching from DB", isbn, e);
                stringRedisTemplate.delete(key);
            }
        }

        // Step 2: SingleFlight — 同一 isbn 只有一个线程打 DB,其他 wait
        CompletableFuture<Book> leader = new CompletableFuture<>();
        CompletableFuture<Book> existing = inflight.putIfAbsent(isbn, leader);

        Book book;
        if (existing == null) {
            // 我是 leader — 负责查 DB + 写缓存
            try {
                book = bookMapper.selectBookByISBN(isbn);
                if (book == null) {
                    // 写负 sentinel(3 分钟)
                    stringRedisTemplate.opsForValue().set(key, NEGATIVE_SENTINEL, Expiration.from(3L, TimeUnit.MINUTES));
                } else {
                    String json = objectMapper.writeValueAsString(book);
                    // TTL 加随机偏移,防雪崩
                    stringRedisTemplate.opsForValue().set(key, json, RandomExpirationTimeWithOffset.get(30L, TimeUnit.MINUTES));
                }
                leader.complete(book);
            } catch (Exception e) {
                leader.completeExceptionally(e);
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "DB query failed: " + e.getMessage(), e);
            } finally {
                inflight.remove(isbn, leader);
            }
        } else {
            // 我是 follower — 等 leader 的结果
            try {
                book = existing.get(5, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "DB query timeout for isbn=" + isbn, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "Interrupted", e);
            } catch (ExecutionException e) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "Leader failed: " + e.getMessage(), e);
            }
        }

        if (book == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "图书不存在, isbn=" + isbn);
        }
        return book;
    }

    // ============== P1 第一个子任务:多条件组合查询 ==============

    @Override
    public PageResult<Book> search(BookSearchRequest req) {
        req.compact();
        int offset = (req.getPage() - 1) * req.getSize();
        int total = bookMapper.countBySearch(req);
        List<Book> books = bookMapper.selectListBySearch(req, offset, req.getSize());
        return new PageResult<>(total, books);
    }

    // ============== 任务二:时间粒度区间查询 ==============

    @Override
    public PageResult<Book> searchByPublishedDateBy(BookPublishedDateByRequest req) {
        req.compact();
        LocalDate[] range = computePublishedDateRange(req);
        int offset = (req.getPage() - 1) * req.getSize();
        int total = bookMapper.countByPublishedDateRange(range[0], range[1]);
        List<Book> books = bookMapper.selectListByPublishedDateRange(range[0], range[1], offset, req.getSize());
        return new PageResult<>(total, books);
    }

    @Override
    public PageResult<Book> searchByCreatedTimeBy(BookDateTimeByRequest req) {
        req.compact();
        LocalDateTime[] range = computeDateTimeRange(req);
        int offset = (req.getPage() - 1) * req.getSize();
        int total = bookMapper.countByCreatedTimeRange(range[0], range[1]);
        List<Book> books = bookMapper.selectListByCreatedTimeRange(range[0], range[1], offset, req.getSize());
        return new PageResult<>(total, books);
    }

    @Override
    public PageResult<Book> searchByUpdatedTimeBy(BookDateTimeByRequest req) {
        req.compact();
        LocalDateTime[] range = computeDateTimeRange(req);
        int offset = (req.getPage() - 1) * req.getSize();
        int total = bookMapper.countByUpdatedTimeRange(range[0], range[1]);
        List<Book> books = bookMapper.selectListByUpdatedTimeRange(range[0], range[1], offset, req.getSize());
        return new PageResult<>(total, books);
    }

    // ============== 区间端点计算(私有工具) ==============

    private static LocalDate[] computePublishedDateRange(BookPublishedDateByRequest req) {
        int year = req.getYear();
        LocalDate start;
        LocalDate end;
        if (req.getMonth() == null) {
            start = LocalDate.of(year, 1, 1);
            end = start.plusYears(1);
        } else if (req.getDay() == null) {
            start = LocalDate.of(year, req.getMonth(), 1);
            end = start.plusMonths(1);
        } else {
            start = LocalDate.of(year, req.getMonth(), req.getDay());
            end = start.plusDays(1);
        }
        return new LocalDate[]{start, end};
    }

    private static LocalDateTime[] computeDateTimeRange(BookDateTimeByRequest req) {
        int year = req.getYear();
        int month = req.getMonth() == null ? 1 : req.getMonth();
        int day = req.getDay() == null ? 1 : req.getDay();
        int hour = req.getHour() == null ? 0 : req.getHour();
        int minute = req.getMinute() == null ? 0 : req.getMinute();
        LocalDateTime start = LocalDateTime.of(year, month, day, hour, minute, 0);
        LocalDateTime end;
        if (req.getMinute() != null) {
            end = start.plusMinutes(1);
        } else if (req.getHour() != null) {
            end = start.plusHours(1);
        } else if (req.getDay() != null) {
            end = start.plusDays(1);
        } else if (req.getMonth() != null) {
            end = start.plusMonths(1);
        } else {
            end = start.plusYears(1);
        }
        return new LocalDateTime[]{start, end};
    }

    // SLF4J — lombok @Slf4j 没启用,手动声明
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BookServiceImpl.class);
}

/**
 * `@Transactional(rollbackFor = Exception.class)` 写在**写操作**上 — `rollbackFor = Exception.class` 表示**任何异常都回滚**(默认只回滚 RuntimeException)
 * `BeanUtils.copyProperties(req, existing)` **不复制 null 字段** → 部分更新只覆盖前端传来的字段
 */