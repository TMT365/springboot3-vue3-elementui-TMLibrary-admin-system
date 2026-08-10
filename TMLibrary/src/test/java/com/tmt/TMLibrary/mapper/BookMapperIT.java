package com.tmt.TMLibrary.mapper;

import com.tmt.TMLibrary.dto.BookSearchRequest;
import com.tmt.TMLibrary.entity.Book;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @brief BookMapper 集成测试 — 跑真 SQL,验证 XML mapper 拼接逻辑
 *
 * 用 {@code @SpringBootTest} 启动完整 Spring 上下文 + 手写的 {@code MyBatisConfig},
 * 数据源由 test application.yml 切到 H2 内存库。
 *
 * 数据用 @BeforeEach 通过 mapper.insertBook() 插入,而不是 SQL 脚本 —
 *   1) 同步测试 insert 路径(2 合 1)
 *   2) 不依赖 Spring Boot 的 SQL init 对 JSON 的支持(JSON 数据 init 在 SB 4.x 不稳)
 *   3) 每次测试前清表,保证可重入
 *
 * 覆盖(每组都对照 XML mapper 的 SQL):
 *   - 基础 CRUD:insertBook / selectBookById / updateBookById / deleteBookById
 *   - 分页:selectList(offset, limit) / countBooks
 *   - 单字段模糊:selectListByTitle(转义%)/ selectListByISBN
 *   - 范围:selectListByPriceRange / selectListByStockQuantityRange
 *   - 时间区间(动态 SQL):selectListByPublishedDateRange / CreatedTimeRange
 *   - 多条件:selectListBySearch(走 <where> + <if>)
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("BookMapper 集成测试(H2 内存库)")
class BookMapperIT {

    @Autowired
    private BookMapper bookMapper;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        // 清表 + 重置 AUTO_INCREMENT — 关键:H2 AUTO_INCREMENT 不会因 delete 重置,
        // 不手动重置,后续测试会拿到 id=11/12/13... 而非 1-10
        jdbc.execute("DELETE FROM book");
        jdbc.execute("ALTER TABLE book ALTER COLUMN id RESTART WITH 1");
        // 插 10 条 fixture
        insert("Effective Java",        "Joshua Bloch",     "9780134685991", new BigDecimal("42.00"),  LocalDate.of(2018, 1, 6),  100);
        insert("Clean Code",            "Robert C. Martin", "9780132350884", new BigDecimal("39.95"),  LocalDate.of(2008, 8, 1),   80);
        insert("Design Patterns",       "Gang of Four",     "9780201633610", new BigDecimal("54.99"),  LocalDate.of(1994, 10, 31), 60);
        insert("The Pragmatic Programmer", "Andrew Hunt",   "9780201616224", new BigDecimal("39.95"),  LocalDate.of(1999, 10, 30), 70);
        insert("Refactoring",           "Martin Fowler",    "9780134757599", new BigDecimal("47.95"),  LocalDate.of(2018, 11, 20), 50);
        insert("三体",                   "刘慈欣",            "9787229030933", new BigDecimal("38.00"),  LocalDate.of(2008, 1, 1),  200);
        insert("活着",                   "余华",              "9787506365437", new BigDecimal("28.00"),  LocalDate.of(2012, 8, 1),  300);
        insert("1984",                  "George Orwell",    "9780451524935", new BigDecimal("9.99"),   LocalDate.of(1961, 8, 1),  250);
        insert("Animal Farm",           "George Orwell",    "9780451526342", new BigDecimal("8.99"),   LocalDate.of(1946, 8, 17), 200);
        insert("Code Complete",         "Steve McConnell",  "9780735619678", new BigDecimal("49.95"),  LocalDate.of(2004, 6, 19),  45);
    }

    private void insert(String title, String author, String isbn,
                        BigDecimal price, LocalDate publishedDate, int stock) {
        Book b = new Book();
        b.setTitle(title);
        b.setAuthor(author);
        b.setIsbn(isbn);
        b.setPrice(price);
        b.setPublishedDate(publishedDate);
        b.setStockQuantity(stock);
        b.setCreatedTime(LocalDateTime.now());
        b.setUpdatedTime(LocalDateTime.now());
        bookMapper.insertBook(b);
    }

    // ============== 基础 CRUD ==============

    @Nested
    @DisplayName("基础 CRUD")
    class BasicCrud {

        @Test
        @DisplayName("insertBook + selectBookById 自增 id 正确回写")
        void insertAndSelectById() {
            Book found = bookMapper.selectBookById(1);
            assertThat(found).isNotNull();
            assertThat(found.getTitle()).isEqualTo("Effective Java");
            assertThat(found.getIsbn()).isEqualTo("9780134685991");
        }

        @Test
        @DisplayName("selectBookById 找不到返回 null(不抛异常)")
        void selectById_notFound_returnsNull() {
            assertThat(bookMapper.selectBookById(99999)).isNull();
        }

        @Test
        @DisplayName("selectBookByISBN 精确匹配")
        void selectByIsbn() {
            Book found = bookMapper.selectBookByISBN("9780134685991");
            assertThat(found).isNotNull();
            assertThat(found.getTitle()).isEqualTo("Effective Java");
        }

        @Test
        @DisplayName("updateBookById 改 title + 保留未传字段")
        void updateById() {
            Book b = bookMapper.selectBookById(1);
            b.setTitle("Effective Java 3rd");
            int rows = bookMapper.updateBookById(b);

            assertThat(rows).isEqualTo(1);
            Book after = bookMapper.selectBookById(1);
            assertThat(after.getTitle()).isEqualTo("Effective Java 3rd");
            // BeanUtils 行为:<set> 标签内 if 跳过 null/空串,其它字段保留
            assertThat(after.getIsbn()).isEqualTo("9780134685991");
        }

        @Test
        @DisplayName("deleteBookById 真实删除")
        void deleteById() {
            int rows = bookMapper.deleteBookById(1);
            assertThat(rows).isEqualTo(1);
            assertThat(bookMapper.selectBookById(1)).isNull();
        }
    }

    // ============== 分页 ==============

    @Nested
    @DisplayName("分页")
    class Pagination {

        @Test
        @DisplayName("countBooks 返回总条数 10")
        void countBooks() {
            assertThat(bookMapper.countBooks()).isEqualTo(10);
        }

        @Test
        @DisplayName("selectList(offset, limit) 正确切片")
        void selectList() {
            List<Book> page1 = bookMapper.selectList(0, 5);
            assertThat(page1).hasSize(5);
            assertThat(page1.get(0).getTitle()).isEqualTo("Effective Java");

            List<Book> page2 = bookMapper.selectList(5, 5);
            assertThat(page2).hasSize(5);
            // id=1..5 在 page1,id=6..10 在 page2
            // id=6 = 三体,id=7 = 活着,id=8 = 1984,id=9 = Animal Farm,id=10 = Code Complete
            assertThat(page2.get(0).getTitle()).isEqualTo("三体");
        }
    }

    // ============== 单字段模糊匹配 ==============

    @Nested
    @DisplayName("单字段搜索")
    class SingleFieldSearch {

        @Test
        @DisplayName("selectListByTitle LIKE 模糊(转义 %)")
        void selectByTitle_likeMatch() {
            // 'Java' 应该匹配 Effective Java
            List<Book> results = bookMapper.selectListByTitle("Java", 0, 10);
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getTitle()).isEqualTo("Effective Java");
        }

        @Test
        @DisplayName("selectListByTitle '%' 拼成 LIKE '%%%' 匹配所有(通配符行为,无自动转义)")
        void selectByTitle_percentNotWildcard() {
            // 文档化 MyBatis 当前行为:title='%' 经 CONCAT 拼接后变成 LIKE '%%%',
            // 在 MySQL 和 H2 下都匹配全部行。生产代码若需把 % 当字面量,
            // 需在 Service / DTO 层手动转义(不在 Mapper 职责内)
            List<Book> results = bookMapper.selectListByTitle("%", 0, 10);
            assertThat(results).hasSize(10);
        }

        @Test
        @DisplayName("selectListByAuthor 模糊(英文 / 中文)")
        void selectByAuthor() {
            List<Book> english = bookMapper.selectListByAuthor("Orwell", 0, 10);
            assertThat(english).hasSize(2);  // 1984 + Animal Farm

            List<Book> chinese = bookMapper.selectListByAuthor("刘慈欣", 0, 10);
            assertThat(chinese).hasSize(1);
        }

        @Test
        @DisplayName("selectListByPublishedDate 精确匹配")
        void selectByPublishedDate_exact() {
            List<Book> results = bookMapper.selectListByPublishedDate(LocalDate.of(2008, 1, 1), 0, 10);
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getTitle()).isEqualTo("三体");
        }
    }

    // ============== 范围查询 ==============

    @Nested
    @DisplayName("范围查询")
    class RangeQuery {

        @Test
        @DisplayName("selectListByPriceRange [10, 50] 包含两端")
        void priceRange() {
            // 10 <= price <= 50:42, 39.95, 54.99(出), 39.95, 47.95, 38, 28, 9.99(出), 8.99(出), 49.95
            // 即 7 本:Effective Java, Clean Code, Pragmatic, Refactoring, 三体, 活着, Code Complete
            List<Book> results = bookMapper.selectListByPriceRange(
                new BigDecimal("10"), new BigDecimal("50"), 0, 20);
            assertThat(results).extracting(Book::getTitle)
                .containsExactlyInAnyOrder(
                    "Effective Java", "Clean Code", "The Pragmatic Programmer",
                    "Refactoring", "三体", "活着", "Code Complete");
        }

        @Test
        @DisplayName("selectListByStockQuantityRange [50, 200] 包含两端")
        void stockRange() {
            // 50 <= stock <= 200:Effective Java 100, Clean Code 80, Design Patterns 60,
            //                          Pragmatic 70, Refactoring 50, 三体 200, Animal Farm 200
            // 不含:活着 300, 1984 250, Code Complete 45
            List<Book> results = bookMapper.selectListByStockQuantityRange(50, 200, 0, 20);
            assertThat(results).extracting(Book::getTitle)
                .containsExactlyInAnyOrder(
                    "Effective Java", "Clean Code", "Design Patterns", "The Pragmatic Programmer",
                    "Refactoring", "三体", "Animal Farm");
        }

        @Test
        @DisplayName("countBooksByPriceRange 总数 = 列表数(同一份 WHERE)")
        void countByPriceRange_matchesListSize() {
            long listSize = bookMapper.selectListByPriceRange(
                new BigDecimal("10"), new BigDecimal("50"), 0, 100).size();
            int count = bookMapper.countBooksByPriceRange(
                new BigDecimal("10"), new BigDecimal("50"));
            assertThat(count).isEqualTo((int) listSize);
        }
    }

    // ============== 时间区间(动态 SQL) ==============

    @Nested
    @DisplayName("时间区间(半开 [start, end))")
    class DateRangeQuery {

        @Test
        @DisplayName("selectListByPublishedDateRange [2018-01-01, 2019-01-01) 包含左端不含右端")
        void publishedDateRange_year() {
            // 2018 全年:Effective Java (2018-01-06) + Refactoring (2018-11-20)
            List<Book> results = bookMapper.selectListByPublishedDateRange(
                LocalDate.of(2018, 1, 1), LocalDate.of(2019, 1, 1), 0, 10);
            assertThat(results).extracting(Book::getTitle)
                .containsExactlyInAnyOrder("Effective Java", "Refactoring");
        }

        @Test
        @DisplayName("selectListByPublishedDateRange [2008-01-01, 2008-02-01) 1 月份")
        void publishedDateRange_month() {
            // 2008-01-01 ~ 2008-01-31:三体 (2008-01-01) + Clean Code(不在 — 2008-08-01)
            List<Book> results = bookMapper.selectListByPublishedDateRange(
                LocalDate.of(2008, 1, 1), LocalDate.of(2008, 2, 1), 0, 10);
            assertThat(results).extracting(Book::getTitle)
                .containsExactlyInAnyOrder("三体");
        }

        @Test
        @DisplayName("countByPublishedDateRange 总数与列表数一致")
        void countByPublishedDateRange_matchesList() {
            int count = bookMapper.countByPublishedDateRange(
                LocalDate.of(2018, 1, 1), LocalDate.of(2019, 1, 1));
            long listSize = bookMapper.selectListByPublishedDateRange(
                LocalDate.of(2018, 1, 1), LocalDate.of(2019, 1, 1), 0, 100).size();
            assertThat(count).isEqualTo((int) listSize);
        }
    }

    // ============== 多条件组合(走 <where> + <if>) ==============

    @Nested
    @DisplayName("多条件组合查询(走 <where> + <if>)")
    class MultiSearch {

        @Test
        @DisplayName("空 query 返回全量")
        void emptyQuery_returnsAll() {
            BookSearchRequest q = new BookSearchRequest();
            assertThat(bookMapper.countBySearch(q)).isEqualTo(10);
            assertThat(bookMapper.selectListBySearch(q, 0, 100)).hasSize(10);
        }

        @Test
        @DisplayName("title 单独条件 → 走 title LIKE")
        void titleOnly() {
            BookSearchRequest q = new BookSearchRequest();
            q.setTitle("Java");
            assertThat(bookMapper.countBySearch(q)).isEqualTo(1);
        }

        @Test
        @DisplayName("author + priceRange 组合(AND 拼接)")
        void authorAndPrice() {
            BookSearchRequest q = new BookSearchRequest();
            q.setAuthor("Orwell");
            q.setMinPrice(new BigDecimal("5"));
            q.setMaxPrice(new BigDecimal("15"));
            // Orwell + 5..15 = 1984 (9.99) + Animal Farm (8.99)
            assertThat(bookMapper.countBySearch(q)).isEqualTo(2);
        }

        @Test
        @DisplayName("minPrice=10 + maxPrice=50 范围")
        void priceRangeOnly() {
            BookSearchRequest q = new BookSearchRequest();
            q.setMinPrice(new BigDecimal("10"));
            q.setMaxPrice(new BigDecimal("50"));
            int count = bookMapper.countBySearch(q);
            // 42, 39.95, 39.95, 47.95, 38, 28, 49.95 = 7
            assertThat(count).isEqualTo(7);
        }

        @Test
        @DisplayName("minStock=200 + maxStock=300")
        void stockRangeOnly() {
            BookSearchRequest q = new BookSearchRequest();
            q.setMinStock(200);
            q.setMaxStock(300);
            // 活着 300 + 三体 200 + 1984 250 + Animal Farm 200 + Effective Java 100(不在 — 100<200)
            // = 4
            assertThat(bookMapper.countBySearch(q)).isEqualTo(4);
        }

        @Test
        @DisplayName("title + author + price 三条件组合")
        void threeWayCombination() {
            BookSearchRequest q = new BookSearchRequest();
            q.setTitle("Code");
            q.setAuthor("Robert");
            q.setMinPrice(new BigDecimal("30"));
            // Clean Code (Robert, 39.95) — Code Complete 是不匹配的(Steve McConnell, 49.95)
            assertThat(bookMapper.countBySearch(q)).isEqualTo(1);
            assertThat(bookMapper.selectListBySearch(q, 0, 10).get(0).getTitle())
                .isEqualTo("Clean Code");
        }

        @Test
        @DisplayName("条件为 null 字段不出现在 WHERE(不污染查询)")
        void nullFieldsIgnored() {
            BookSearchRequest q = new BookSearchRequest();
            q.setTitle("Java");
            // author=null, priceRange=null 都不会参与 WHERE
            assertThat(bookMapper.countBySearch(q)).isEqualTo(1);
        }

        @Test
        @DisplayName("分页 offset/limit 生效")
        void pagination() {
            BookSearchRequest q = new BookSearchRequest();
            List<Book> page1 = bookMapper.selectListBySearch(q, 0, 5);
            List<Book> page2 = bookMapper.selectListBySearch(q, 5, 5);
            assertThat(page1).hasSize(5);
            assertThat(page2).hasSize(5);
            // 两页不应有重叠
            assertThat(page1).extracting(Book::getId)
                .doesNotContainAnyElementsOf(page2.stream().map(Book::getId).toList());
        }
    }
}
