package com.tmt.TMLibrary.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.tmt.TMLibrary.dto.BookSearchRequest;
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
         * @brief 插入一本书
         * @param book 要插入的书籍
         * @return 插入的记录数
         * 
         */
        int insertBook(Book book);

        /**
         * @brief 根据ID删除一本书
         * @param id
         * @return 删除的记录数
         */
        int deleteBookById(@Param("id") int id);

        /**
         * @brief 根据ISBN删除一本书
         * @param isbn
         * @return 删除的记录数
         */
        int deleteBookByISBN(@Param("isbn") String isbn);

        /**
         * @brief 更新一本书的信息
         * @param book
         * @return 更新的记录数
         */
        int updateBookById(Book book);

        /**
         * @brief 根据ISBN更新一本书的信息
         * @param book
         * @return 更新的记录数
         */
        int updateBookByISBN(Book book);

        /**
         * @brief 根据ID查询一本书
         * @param id
         * @return Book对象，如果未找到则返回null
         */
        Book selectBookById(@Param("id") int id);

        /**
         * @brief 根据ISBN查询一本书
         * @param isbn
         * @return Book对象，如果未找到则返回null
         */
        Book selectBookByISBN(@Param("isbn") String isbn);

        /**
         * @brief 查询所有书籍
         * @param offset 查询的起始位置（用于分页）
         * @param limit  限制返回的记录数
         * @return 包含Book对象的列表
         */
        List<Book> selectList(@Param("offset") int offset, @Param("limit") int limit);

        /**
         * @brief 查询书籍总数
         * @return
         */
        Integer countBooks();
        // mybatis的xml映射文件，不支持函数重载的写法，因为在xml中，方法名是唯一标识符，不能有相同的方法名，即使参数不同也不行。

        /**
         * @brief 根据title查询书籍列表
         * @param title  书籍标题的关键字 进行模糊查询
         * @param offset 查询的起始位置（用于分页）
         * @param limit  限制返回的记录数
         * @return 包含Book对象的列表
         */
        List<Book> selectListByTitle(@Param("title") String title, @Param("offset") int offset,
                        @Param("limit") int limit);

        /**
         * @brief 根据title模糊查询符号书籍总数
         * @param title
         * @return
         */
        Integer countBooksByTitle(@Param("title") String title);

        /**
         * @brief 根据keyword查询书籍列表
         * @param author 作者的关键字
         * @param offset 查询的起始位置（用于分页）
         * @param limit  限制返回的记录数
         * @return 包含Book对象的列表
         */
        List<Book> selectListByAuthor(@Param("author") String author, @Param("offset") int offset,
                        @Param("limit") int limit);

        /**
         * @brief 根据author模糊查询符号书籍总数
         * @param author
         * @return
         */
        Integer countBooksByAuthor(@Param("author") String author);

        /**
         * @brief 根据(出版日期)查询书籍列表
         * @param publishedDate 出版日期
         * @param offset        查询的起始位置（用于分页）
         * @param limit         限制返回的记录数
         * @return 包含Book对象的列表
         */
        List<Book> selectListByPublishedDate(@Param("publishedDate") LocalDate publishedDate,
                        @Param("offset") int offset,
                        @Param("limit") int limit);

        /**
         * @brief 根据publishedDate模糊查询符号书籍总数
         * @param publishedDate
         * @return
         */
        Integer countBooksByPublishedDate(@Param("publishedDate") LocalDate publishedDate);

        /**
         * @brief 根据(创建日期)查询书籍列表
         * @param createdTime 创建日期
         * @param offset      查询的起始位置（用于分页）
         * @param limit       限制返回的记录数
         * @return 包含Book对象的列表
         */
        List<Book> selectListByCreatedTime(@Param("createdTime") LocalDateTime createdTime, @Param("offset") int offset,
                        @Param("limit") int limit);

        /**
         * @brief 根据createdTime模糊查询符号书籍总数
         * @param createdTime
         * @return
         */
        Integer countBooksByCreatedTime(@Param("createdTime") LocalDateTime createdTime);

        /**
         * @brief 根据(更新日期)查询书籍列表
         * @param updateTime 更新日期
         * @param offset     查询的起始位置（用于分页）
         * @param limit      限制返回的记录数
         * @return 包含Book对象的列表
         */
        List<Book> selectListByUpdatedTime(@Param("updateTime") LocalDateTime updateTime, @Param("offset") int offset,
                        @Param("limit") int limit);

        /**
         * @brief 根据updateTime模糊查询符号书籍总数
         * @param updateTime
         * @return
         */
        Integer countBooksByUpdatedTime(@Param("updateTime") LocalDateTime updateTime);

        /**
         * @brief 根据价格范围查询书籍列表
         * @param minPrice
         * @param maxPrice
         * @param offset
         * @param limit
         * @return
         */
        List<Book> selectListByPriceRange(@Param("minPrice") BigDecimal minPrice,
                        @Param("maxPrice") BigDecimal maxPrice,
                        @Param("offset") int offset, @Param("limit") int limit);

        /**
         * @brief 根据价格范围查询书籍总数
         * @param minPrice
         * @param maxPrice
         * @return
         */
        Integer countBooksByPriceRange(@Param("minPrice") BigDecimal minPrice, @Param("maxPrice") BigDecimal maxPrice);

        /**
         * @brief 根据库存数量范围查询书籍列表
         * @param minStock
         * @param maxStock
         * @param offset
         * @param limit
         * @return
         */
        List<Book> selectListByStockQuantityRange(@Param("minStock") int minStock, @Param("maxStock") int maxStock,
                        @Param("offset") int offset, @Param("limit") int limit);

        /**
         * @brief 根据库存数量范围查询书籍总数
         * @param minStock
         * @param maxStock
         * @return
         */
        Integer countBooksByStockQuantityRange(@Param("minStock") int minStock, @Param("maxStock") int maxStock);

        // ============== P1 第一个子任务:多条件组合查询 ==============

        /**
         * @brief 多条件组合查询 — 由 BookMapper.xml 里 <where> + <if> 动态拼接
         * @param q      查询条件(已归一化)
         * @param offset 分页起始
         * @param limit  分页大小
         */
        List<Book> selectListBySearch(@Param("q") BookSearchRequest q,
                        @Param("offset") int offset,
                        @Param("limit") int limit);

        /**
         * @brief 多条件组合查询的总数 — 与 selectListBySearch 同一份 WHERE 逻辑
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

        Book selectByIdForUpdate(@Param("id") int id);

        int atomicDecrementStock(@Param("id") int id, @Param("decrement") int decrement);

        int atomicIncrementStock(@Param("id") int id, @Param("increment") int increment);
}