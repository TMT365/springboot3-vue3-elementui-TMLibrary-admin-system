package com.tmt.TMLibrary.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.tmt.TMLibrary.dto.request.BookSearchRequest;
import com.tmt.TMLibrary.entity.Book;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Mapper
public interface BookMapper {
        // 这里定义了Book实体类的数据库操作方法，例如增删改查等。
        // 你可以使用MyBatis的注解或者XML映射文件来实现这些方法。

        /**
         * 插入一本书
         * @param book 要插入的书籍
         * @return 插入的记录数
         * 
         */
        int insertBook(Book book);


        /**
         * 根据ISBN删除一本书
         * @param isbn
         * @return 删除的记录数
         */
        int deleteBookByISBN(@Param("isbn") String isbn);


        /**
         * 根据ISBN更新一本书的信息
         * @param book
         * @return 更新的记录数
         */
        int updateBookByISBN(Book book);


        /**
         * 根据ISBN查询一本书
         * @param isbn
         * @return Book对象，如果未找到则返回null
         */
        Book selectBookByISBN(@Param("isbn") String isbn);

        /**
         * 查询所有书籍
         * @param offset 查询的起始位置（用于分页）
         * @param limit  限制返回的记录数
         * @return 包含Book对象的列表
         */
        List<Book> selectList(@Param("offset") int offset, @Param("limit") int limit);

        /**
         * 查询书籍总数
         * @return
         */
        Integer countBooks();

        // ============== P1 第一个子任务:多条件组合查询 ==============

        /**
         * 多条件组合查询 — 由 BookMapper.xml 里 {@code <where>} + {@code <if>} 动态拼接
         * @param q      查询条件(已归一化)
         * @param offset 分页起始
         * @param limit  分页大小
         */
        List<Book> selectListBySearch(@Param("q") BookSearchRequest q,
                        @Param("offset") int offset,
                        @Param("limit") int limit);

        /**
         * 多条件组合查询的总数 — 与 selectListBySearch 同一份 WHERE 逻辑
         */
        Integer countBySearch(@Param("q") BookSearchRequest q);

        // ============== 任务二:时间粒度区间查询 ==============

        // published_date(半开区间 [start, end))
        List<Book> selectListByPublishedDateRange(@Param("start") LocalDate start,
                        @Param("end") LocalDate end,
                        @Param("offset") int offset,
                        @Param("limit") int limit);

        Integer countByPublishedDateRange(@Param("start") LocalDate start,
                        @Param("end") LocalDate end);

        // created_time(半开区间 [start, end))
        List<Book> selectListByCreatedTimeRange(@Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end,
                        @Param("offset") int offset,
                        @Param("limit") int limit);

        Integer countByCreatedTimeRange(@Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);

        // updated_time(半开区间 [start, end))
        List<Book> selectListByUpdatedTimeRange(@Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end,
                        @Param("offset") int offset,
                        @Param("limit") int limit);

        Integer countByUpdatedTimeRange(@Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);

        // 非锁的按主键查询 — 用于 Redis 预热，避免在事务中触发 FOR UPDATE
        Book selectById(@Param("id") int id);
}