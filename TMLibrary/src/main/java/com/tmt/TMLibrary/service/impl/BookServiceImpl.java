package com.tmt.TMLibrary.service.impl;

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

import java.time.LocalDateTime;
import java.time.LocalDate;

import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.util.concurrent.TimeUnit;
import com.tmt.TMLibrary.common.utils.RandomExpirationTimeWithOffset;



@Service
// @RequiredArgsConstructor //lombok注解
public class BookServiceImpl implements BookService {
    // 这里实现了BookService接口中的方法，调用BookMapper进行数据库操作。
    // 你可以在这里添加业务逻辑，例如验证输入数据、处理异常等。

    private final BookMapper bookMapper;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String REDIS_BOOKS_INFO_PATH = "tmlibrary:user:books:";

    // 唯一构造器，Spring自动调用，把容器中的bookMapper传进来，不需要写@Autowired
    public BookServiceImpl(BookMapper bookMapper, StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.bookMapper = bookMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        // Mybatis 已经帮我们实现了 BookMapper 接口的动态代理对象，并放在IOC中，Spring 会自动注入到这里。
    }

    @Override
    public PageResult<Book> page(int page, int size) {
        // 不走Redis，直接查询 MySql
        int offset = (page -1) * size;
        int total = bookMapper.countBooks();
        List<Book> books = bookMapper.selectList(offset, size);
        return new PageResult<>(total, books);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(BookSaveRequest request) {
        // 这里实现创建图书信息的逻辑，例如调用BookMapper的插入方法。
        Book book = new Book();
        // 在创建图书这里，有2个属性是无法从BookSaveRequest中获取的，分别是id和createTime。id是自增的，createTime是当前时间，所以我们不需要从请求中获取它们。
        // 这里可以手动调用Book的Getter和Setter方法来设置属性值，或者使用BeanUtils.copyProperties()方法来复制属性值。
        BeanUtils.copyProperties(request, book);
        // BeanUtils.copyProperties()方法会将request中的属性值复制到book中，如果request中有属性值为null，则不会覆盖book中已有的属性值。
        book.setCreatedTime(LocalDateTime.now());

        bookMapper.insertBook(book);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByISBN(String isbn) {
        // 先删除数据库
        Book book = bookMapper.selectBookByISBN(isbn);
        if (book == null) {
            throw new BusinessException(ResultCode.NOT_FOUND,"图书不存在, isbn=" + isbn);
        }
        int rowsAffected = bookMapper.deleteBookByISBN(isbn);
        if (rowsAffected > 0) {
            // 执行删除 Redis
            stringRedisTemplate.delete(REDIS_BOOKS_INFO_PATH + isbn);

        }
        return rowsAffected;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByISBN(String isbn, BookUpdateRequest request) {
        // 先更新 MySql
        Book book = bookMapper.selectBookByISBN(isbn);
        if (book == null) {
            throw new BusinessException(ResultCode.NOT_FOUND,"图书不存在, isbn=" + isbn);
        }
        BeanUtils.copyProperties(request, book);
        book.setUpdatedTime(LocalDateTime.now());
        int rowsAffected = bookMapper.updateBookByISBN(book);
        if (rowsAffected > 0) {
            // 在删除 Redis
            stringRedisTemplate.delete(REDIS_BOOKS_INFO_PATH + isbn);
        }
        // 为什么要延迟双删？
        return rowsAffected;
    }

    @Override
    public Book getByISBN(String isbn) {
        // 先查询 Redis
        String jsonString = stringRedisTemplate.opsForValue().get(REDIS_BOOKS_INFO_PATH + isbn);

        Book book;
        if (jsonString == null) {
            // 查询数据库
            book = bookMapper.selectBookByISBN(isbn);
            if (book == null) {
                // 如果数据库没有，防止缓存击穿，设置一个TTL时间短的空值
                stringRedisTemplate.opsForValue().set(REDIS_BOOKS_INFO_PATH + isbn, "", Expiration.from(3L, TimeUnit.MINUTES));
                throw new BusinessException(ResultCode.NOT_FOUND, "图书不存在, isbn=" + isbn);
            }
            String json = objectMapper.writeValueAsString(book);
            // 同样写入带偏移量的TTL
            stringRedisTemplate.opsForValue().set(REDIS_BOOKS_INFO_PATH + isbn, json, RandomExpirationTimeWithOffset.get(30L, TimeUnit.MINUTES));

        } else {
            // 反序列化
            book = objectMapper.readValue(jsonString, Book.class);
        }

        if (book == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "图书不存在, isbn=" + isbn);
        }

        return book;
    }

    // 这些区间查询无法走 Redis 查询，直接走数据库比 Redis 好

    // ============== P1 第一个子任务:多条件组合查询 ==============

    @Override
    public PageResult<Book> search(BookSearchRequest req) {
        req.compact();                                   // 1. 归一化(空串→null、负数→null、page/size 兜底)
        int offset = (req.getPage() - 1) * req.getSize();
        int total = bookMapper.countBySearch(req);       // 2. 走同一份动态 WHERE
        List<Book> books = bookMapper.selectListBySearch(req, offset, req.getSize());
        return new PageResult<>(total, books);
    }

    // ============== 任务二:时间粒度区间查询 ==============

    @Override
    public PageResult<Book> searchByPublishedDateBy(BookPublishedDateByRequest req) {
        req.compact();                                   // 1. 归一化 + 粒度连续性校验(失败抛 400)
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

    /**
     * 根据 year/month/day 算半开区间 [start, end)
     *        year 必填;month/day 由 compact() 保证连续性
     * @return [start, end]
     */
    private static LocalDate[] computePublishedDateRange(BookPublishedDateByRequest req) {
        int year = req.getYear();
        LocalDate start;
        LocalDate end;
        if (req.getMonth() == null) {
            // year-only: [year-01-01, year+1-01-01)
            start = LocalDate.of(year, 1, 1);
            end = start.plusYears(1);
        } else if (req.getDay() == null) {
            // year+month: [year-month-01, year-month+1-01) — LocalDate.plusMonths 处理 12 月跨年
            start = LocalDate.of(year, req.getMonth(), 1);
            end = start.plusMonths(1);
        } else {
            // year+month+day: [year-month-day, year-month-day+1) — LocalDate.plusDays 处理月末/年末
            start = LocalDate.of(year, req.getMonth(), req.getDay());
            end = start.plusDays(1);
        }
        return new LocalDate[]{start, end};
    }

    /**
     * 根据 year/month/day/hour/minute 算半开区间 [start, end)
     *        year 必填;其余由 compact() 保证连续性
     * @return [start, end]
     */
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
}
/**
 * `@Transactional(rollbackFor = Exception.class)` 写在**写操作**上 — `rollbackFor = Exception.class` 表示**任何异常都回滚**(默认只回滚 RuntimeException)
 * `BeanUtils.copyProperties(req, existing)` **不复制 null 字段** → 部分更新只覆盖前端传来的字段
 */