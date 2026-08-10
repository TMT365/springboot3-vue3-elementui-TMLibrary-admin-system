package com.tmt.TMLibrary.service;

import com.tmt.TMLibrary.entity.Book;
import com.tmt.TMLibrary.common.Result.PageResult;
import com.tmt.TMLibrary.dto.BookPublishedDateByRequest;
import com.tmt.TMLibrary.dto.BookDateTimeByRequest;
import com.tmt.TMLibrary.dto.BookSaveRequest;
import com.tmt.TMLibrary.dto.BookSearchRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public interface BookService {
    // 这里定义了BookService接口中的方法，例如增删改查等。
    // 你可以在这里添加业务逻辑，例如验证输入数据、处理异常等。

    /**
     * @brief 分页查询图书信息
     * @param page  页码
     * @param size  每页大小
     * @return
     */
    public abstract PageResult<Book> page(int page, int size);

    /**
     * @brief 根据ID查询图书信息
     * @param id
     * @return
     */
    public abstract Book getById(int id);

    /**
     * @brief 创建图书信息
     * @param request
     */
    public abstract void create(BookSaveRequest request);

    /**
     * @brief 根据ID更新图书信息
     * @param id
     * @param request
     */
    public abstract int updateById(int id, BookSaveRequest request);

    /**
     * @brief 根据ID删除图书信息 
     * @param id
     */
    public abstract int deleteById(int id);

    public abstract int deleteByISBN(String isbn);

    public abstract int updateByISBN(String isbn, BookSaveRequest request);

    public abstract Book getByISBN(String isbn);

    public abstract PageResult<Book> searchByTitle(String title, int page, int size);

    public abstract PageResult<Book> searchByAuthor(String author, int page, int size);

    public abstract PageResult<Book> searchByPublishedDate(LocalDate publishedDate, int page, int size);

    public abstract PageResult<Book> searchByCreatedTime(LocalDateTime createdTime, int page, int size);

    public abstract PageResult<Book> searchByUpdatedTime(LocalDateTime updatedTime, int page, int size);

    public abstract PageResult<Book> searchByPriceRange(BigDecimal minPrice, BigDecimal maxPrice,int page, int size);

    public abstract PageResult<Book> searchByStockQuantityRange(int minStock, int maxStock, int page, int size);

    // ============== P1 第一个子任务:多条件组合查询 ==============

    /**
     * @brief 多条件组合查询(走动态 SQL),Service 层负责参数归一化
     * @param query  查询条件 + 分页(page/size 在 DTO.compact() 里已兜底)
     */
    public abstract PageResult<Book> search(BookSearchRequest query);

    // ============== 任务二:时间粒度区间查询 ==============

    /**
     * @brief 按 publishedDate 粒度查询(year / year+month / year+month+day)
     *        Service 端根据 DTO 填了哪些字段算 [start, end) 区间端点
     * @param req 已归一化的粒度请求(粒度连续性校验在 compact() 里完成)
     */
    public abstract PageResult<Book> searchByPublishedDateBy(BookPublishedDateByRequest req);

    /**
     * @brief 按 createdTime 粒度查询(year / year+month / year+month+day /
     *        year+month+day+hour / year+month+day+hour+minute)
     */
    public abstract PageResult<Book> searchByCreatedTimeBy(BookDateTimeByRequest req);

    /**
     * @brief 按 updatedTime 粒度查询(粒度同 createdTime)
     */
    public abstract PageResult<Book> searchByUpdatedTimeBy(BookDateTimeByRequest req);
}