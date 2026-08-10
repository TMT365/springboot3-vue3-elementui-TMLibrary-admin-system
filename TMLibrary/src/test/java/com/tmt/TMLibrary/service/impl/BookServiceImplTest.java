package com.tmt.TMLibrary.service.impl;

import com.tmt.TMLibrary.common.Result.PageResult;
import com.tmt.TMLibrary.common.Result.ResultCode;
import com.tmt.TMLibrary.dto.BookDateTimeByRequest;
import com.tmt.TMLibrary.dto.BookPublishedDateByRequest;
import com.tmt.TMLibrary.dto.BookSaveRequest;
import com.tmt.TMLibrary.dto.BookSearchRequest;
import com.tmt.TMLibrary.entity.Book;
import com.tmt.TMLibrary.exception.BusinessException;
import com.tmt.TMLibrary.mapper.BookMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @brief BookServiceImpl 业务单测 — Mockito 风格
 *
 * 覆盖范围(按 Service 方法分块):
 *   - 简单查询:page/getById/getByISBN
 *   - 写操作:create/updateById/deleteById/deleteByISBN/updateByISBN
 *   - 业务异常:NotFound 抛 BusinessException(NOT_FOUND)
 *   - 单字段搜索:searchByTitle/Author/PublishedDate/CreatedTime/UpdatedTime/PriceRange/StockQuantityRange
 *   - 多条件搜索:search(走动态 SQL + 归一化)
 *   - 粒度区间搜索:searchByPublishedDateBy/CreatedTimeBy/UpdatedTimeBy
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BookServiceImpl 业务逻辑测试")
class BookServiceImplTest {

    @Mock
    private BookMapper bookMapper;

    private BookServiceImpl bookService;

    @BeforeEach
    void setUp() {
        bookService = new BookServiceImpl(bookMapper);
    }

    // ============== 通用 helper ==============

    private static Book newBook(int id) {
        Book b = new Book();
        b.setId(id);
        b.setTitle("Book-" + id);
        b.setAuthor("Author-" + id);
        b.setIsbn("ISBN-" + id);
        b.setPrice(new BigDecimal("10.00"));
        b.setStockQuantity(1);
        b.setPublishedDate(LocalDate.of(2024, 1, 1));
        return b;
    }

    private static BookSaveRequest newSaveRequest() {
        BookSaveRequest req = new BookSaveRequest();
        req.setTitle("Effective Java");
        req.setAuthor("Joshua Bloch");
        req.setIsbn("9780134685991");
        req.setPrice(new BigDecimal("42.00"));
        req.setStockQuantity(100);
        req.setPublishedDate(LocalDate.of(2018, 1, 6));
        return req;
    }

    // ============== page ==============

    @Nested
    @DisplayName("分页查询")
    class Page {

        @Test
        @DisplayName("page=1,size=10 → offset=0,调用 count + selectList")
        void page_offsetComputed() {
            when(bookMapper.countBooks()).thenReturn(100);
            when(bookMapper.selectList(0, 10)).thenReturn(List.of(newBook(1), newBook(2)));

            PageResult<Book> result = bookService.page(1, 10);

            assertThat(result.getTotal()).isEqualTo(100);
            assertThat(result.getData()).hasSize(2);
            verify(bookMapper).selectList(0, 10);
        }

        @Test
        @DisplayName("page=3,size=5 → offset=10")
        void page_thirdPage_offsetIs10() {
            when(bookMapper.countBooks()).thenReturn(20);
            when(bookMapper.selectList(10, 5)).thenReturn(List.of());

            bookService.page(3, 5);

            verify(bookMapper).selectList(10, 5);
        }
    }

    // ============== getById / getByISBN ==============

    @Nested
    @DisplayName("按 ID / ISBN 查询")
    class GetByIdOrIsbn {

        @Test
        @DisplayName("getById 存在 → 返回 Book")
        void getById_found() {
            when(bookMapper.selectBookById(1)).thenReturn(newBook(1));

            Book result = bookService.getById(1);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1);
        }

        @Test
        @DisplayName("getById 找不到 → 抛 BusinessException(NOT_FOUND) 且 msg 含 id")
        void getById_notFound_throws() {
            when(bookMapper.selectBookById(999)).thenReturn(null);

            assertThatThrownBy(() -> bookService.getById(999))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.NOT_FOUND.getCode())
                .hasMessageContaining("999");
        }

        @Test
        @DisplayName("getByISBN 存在 → 返回 Book")
        void getByISBN_found() {
            when(bookMapper.selectBookByISBN("9780134685991")).thenReturn(newBook(1));

            Book result = bookService.getByISBN("9780134685991");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("getByISBN 找不到 → 抛 BusinessException(NOT_FOUND) 且 msg 含 isbn")
        void getByISBN_notFound_throws() {
            when(bookMapper.selectBookByISBN("missing")).thenReturn(null);

            assertThatThrownBy(() -> bookService.getByISBN("missing"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.NOT_FOUND.getCode())
                .hasMessageContaining("missing");
        }
    }

    // ============== create ==============

    @Nested
    @DisplayName("创建图书")
    class Create {

        @Test
        @DisplayName("create 调用 insertBook 一次,BeanUtils 复制字段,并设 createdTime")
        void create_setsCreatedTimeAndCopiesFields() {
            BookSaveRequest req = newSaveRequest();
            ArgumentCaptor<Book> captor = ArgumentCaptor.forClass(Book.class);

            bookService.create(req);

            verify(bookMapper, times(1)).insertBook(captor.capture());
            Book saved = captor.getValue();
            assertThat(saved.getTitle()).isEqualTo("Effective Java");
            assertThat(saved.getAuthor()).isEqualTo("Joshua Bloch");
            assertThat(saved.getIsbn()).isEqualTo("9780134685991");
            assertThat(saved.getPrice()).isEqualByComparingTo(new BigDecimal("42.00"));
            assertThat(saved.getStockQuantity()).isEqualTo(100);
            assertThat(saved.getPublishedDate()).isEqualTo(LocalDate.of(2018, 1, 6));
            // createdTime 在调用前由 Service 设置(注入 mock 时刻)
            assertThat(saved.getCreatedTime()).isNotNull();
        }
    }

    // ============== updateById ==============

    @Nested
    @DisplayName("按 ID 更新")
    class UpdateById {

        @Test
        @DisplayName("updateById 存在 → 调用 updateBookById 一次,设 updatedTime")
        void updateById_found() {
            when(bookMapper.selectBookById(1)).thenReturn(newBook(1));
            when(bookMapper.updateBookById(any(Book.class))).thenReturn(1);

            BookSaveRequest req = newSaveRequest();
            int rows = bookService.updateById(1, req);

            assertThat(rows).isEqualTo(1);
            verify(bookMapper).updateBookById(any(Book.class));
        }

        @Test
        @DisplayName("updateById 找不到 → 抛 BusinessException(NOT_FOUND),不调 update")
        void updateById_notFound_throws() {
            when(bookMapper.selectBookById(999)).thenReturn(null);

            assertThatThrownBy(() -> bookService.updateById(999, newSaveRequest()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.NOT_FOUND.getCode())
                .hasMessageContaining("999");

            verify(bookMapper, never()).updateBookById(any(Book.class));
        }
    }

    // ============== deleteById / deleteByISBN ==============

    @Nested
    @DisplayName("删除")
    class Delete {

        @Test
        @DisplayName("deleteById 存在 → 调用 deleteBookById 一次,返回受影响行数")
        void deleteById_found() {
            when(bookMapper.selectBookById(1)).thenReturn(newBook(1));
            when(bookMapper.deleteBookById(1)).thenReturn(1);

            int rows = bookService.deleteById(1);

            assertThat(rows).isEqualTo(1);
            verify(bookMapper).deleteBookById(1);
        }

        @Test
        @DisplayName("deleteById 找不到 → 抛 BusinessException(NOT_FOUND),不调 delete")
        void deleteById_notFound_throws() {
            when(bookMapper.selectBookById(999)).thenReturn(null);

            assertThatThrownBy(() -> bookService.deleteById(999))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.NOT_FOUND.getCode());

            verify(bookMapper, never()).deleteBookById(anyInt());
        }

        @Test
        @DisplayName("deleteByISBN 存在 → 调用 deleteBookByISBN 一次")
        void deleteByISBN_found() {
            when(bookMapper.selectBookByISBN("9780134685991")).thenReturn(newBook(1));
            when(bookMapper.deleteBookByISBN("9780134685991")).thenReturn(1);

            int rows = bookService.deleteByISBN("9780134685991");

            assertThat(rows).isEqualTo(1);
        }

        @Test
        @DisplayName("deleteByISBN 找不到 → 抛 BusinessException(NOT_FOUND)")
        void deleteByISBN_notFound_throws() {
            when(bookMapper.selectBookByISBN("missing")).thenReturn(null);

            assertThatThrownBy(() -> bookService.deleteByISBN("missing"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.NOT_FOUND.getCode());
        }
    }

    // ============== updateByISBN ==============

    @Nested
    @DisplayName("按 ISBN 更新")
    class UpdateByIsbn {

        @Test
        @DisplayName("updateByISBN 存在 → 调用 updateBookByISBN 一次")
        void updateByISBN_found() {
            when(bookMapper.selectBookByISBN("9780134685991")).thenReturn(newBook(1));
            when(bookMapper.updateBookByISBN(any(Book.class))).thenReturn(1);

            int rows = bookService.updateByISBN("9780134685991", newSaveRequest());

            assertThat(rows).isEqualTo(1);
            verify(bookMapper).updateBookByISBN(any(Book.class));
        }

        @Test
        @DisplayName("updateByISBN 找不到 → 抛 BusinessException(NOT_FOUND),不调 update")
        void updateByISBN_notFound_throws() {
            when(bookMapper.selectBookByISBN("missing")).thenReturn(null);

            assertThatThrownBy(() -> bookService.updateByISBN("missing", newSaveRequest()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.NOT_FOUND.getCode());

            verify(bookMapper, never()).updateBookByISBN(any(Book.class));
        }
    }

    // ============== 单字段搜索 ==============

    @Nested
    @DisplayName("单字段搜索")
    class SingleFieldSearch {

        @Test
        @DisplayName("searchByTitle 正确计算 offset=20")
        void searchByTitle_offset20() {
            when(bookMapper.countBooksByTitle("Java")).thenReturn(50);
            when(bookMapper.selectListByTitle("Java", 20, 10)).thenReturn(List.of(newBook(3)));

            PageResult<Book> result = bookService.searchByTitle("Java", 3, 10);

            assertThat(result.getTotal()).isEqualTo(50);
            assertThat(result.getData()).hasSize(1);
            verify(bookMapper).selectListByTitle("Java", 20, 10);
        }

        @Test
        @DisplayName("searchByAuthor 正确计算 offset=5")
        void searchByAuthor_offset5() {
            when(bookMapper.countBooksByAuthor("Bloch")).thenReturn(5);
            when(bookMapper.selectListByAuthor("Bloch", 5, 5)).thenReturn(List.of());

            bookService.searchByAuthor("Bloch", 2, 5);

            verify(bookMapper).selectListByAuthor("Bloch", 5, 5);
        }

        @Test
        @DisplayName("searchByPublishedDate 传入 LocalDate")
        void searchByPublishedDate_passesDate() {
            LocalDate date = LocalDate.of(2024, 6, 15);
            when(bookMapper.countBooksByPublishedDate(date)).thenReturn(1);
            when(bookMapper.selectListByPublishedDate(date, 0, 10))
                .thenReturn(List.of(newBook(1)));

            PageResult<Book> result = bookService.searchByPublishedDate(date, 1, 10);

            assertThat(result.getTotal()).isEqualTo(1);
            verify(bookMapper).selectListByPublishedDate(date, 0, 10);
        }

        @Test
        @DisplayName("searchByCreatedTime 传入 LocalDateTime")
        void searchByCreatedTime_passesDateTime() {
            LocalDateTime ts = LocalDateTime.of(2024, 6, 15, 10, 30);
            when(bookMapper.countBooksByCreatedTime(ts)).thenReturn(0);
            when(bookMapper.selectListByCreatedTime(ts, 0, 10)).thenReturn(List.of());

            bookService.searchByCreatedTime(ts, 1, 10);

            verify(bookMapper).selectListByCreatedTime(ts, 0, 10);
        }

        @Test
        @DisplayName("searchByUpdatedTime 传入 LocalDateTime")
        void searchByUpdatedTime_passesDateTime() {
            LocalDateTime ts = LocalDateTime.of(2024, 6, 15, 10, 30);
            when(bookMapper.countBooksByUpdatedTime(ts)).thenReturn(0);
            when(bookMapper.selectListByUpdatedTime(ts, 0, 10)).thenReturn(List.of());

            bookService.searchByUpdatedTime(ts, 1, 10);

            verify(bookMapper).selectListByUpdatedTime(ts, 0, 10);
        }

        @Test
        @DisplayName("searchByPriceRange 传入 minPrice/maxPrice")
        void searchByPriceRange_passesRange() {
            BigDecimal min = new BigDecimal("10.00");
            BigDecimal max = new BigDecimal("50.00");
            when(bookMapper.countBooksByPriceRange(min, max)).thenReturn(2);
            when(bookMapper.selectListByPriceRange(min, max, 0, 10)).thenReturn(List.of());

            bookService.searchByPriceRange(min, max, 1, 10);

            verify(bookMapper).selectListByPriceRange(min, max, 0, 10);
        }

        @Test
        @DisplayName("searchByStockQuantityRange 传入 minStock/maxStock")
        void searchByStockQuantityRange_passesRange() {
            when(bookMapper.countBooksByStockQuantityRange(5, 100)).thenReturn(3);
            when(bookMapper.selectListByStockQuantityRange(5, 100, 0, 10)).thenReturn(List.of());

            bookService.searchByStockQuantityRange(5, 100, 1, 10);

            verify(bookMapper).selectListByStockQuantityRange(5, 100, 0, 10);
        }
    }

    // ============== 多条件搜索(动态 SQL) ==============

    @Nested
    @DisplayName("多条件搜索 search(动态 SQL)")
    class MultiSearch {

        @Test
        @DisplayName("空 query → page=1,size=10 兜底,offset=0")
        void search_emptyQuery_fallbackApplied() {
            BookSearchRequest req = new BookSearchRequest();
            when(bookMapper.countBySearch(any())).thenReturn(2);
            when(bookMapper.selectListBySearch(any(), eq(0), eq(10)))
                .thenReturn(List.of(newBook(1), newBook(2)));

            PageResult<Book> result = bookService.search(req);

            assertThat(result.getTotal()).isEqualTo(2);
            assertThat(req.getPage()).isEqualTo(1);
            assertThat(req.getSize()).isEqualTo(10);
            verify(bookMapper).selectListBySearch(any(), eq(0), eq(10));
        }

        @Test
        @DisplayName("page=3,size=5 → offset=10")
        void search_offsetComputed() {
            BookSearchRequest req = new BookSearchRequest();
            req.setPage(3);
            req.setSize(5);
            when(bookMapper.countBySearch(any())).thenReturn(0);
            when(bookMapper.selectListBySearch(any(), eq(10), eq(5))).thenReturn(List.of());

            bookService.search(req);

            verify(bookMapper).selectListBySearch(any(), eq(10), eq(5));
        }

        @Test
        @DisplayName("minPrice=-1 归一化为 null,size=10000 clamp 到 100")
        void search_normalizationApplied() {
            BookSearchRequest req = new BookSearchRequest();
            req.setMinPrice(new BigDecimal("-1"));
            req.setSize(10000);
            when(bookMapper.countBySearch(any())).thenReturn(0);
            when(bookMapper.selectListBySearch(any(), anyInt(), eq(100))).thenReturn(List.of());

            bookService.search(req);

            assertThat(req.getMinPrice()).isNull();
            assertThat(req.getSize()).isEqualTo(100);
            verify(bookMapper).selectListBySearch(any(), anyInt(), eq(100));
        }

        @Test
        @DisplayName("title=\"  Java  \" 被 trim")
        void search_titleTrimmed() {
            BookSearchRequest req = new BookSearchRequest();
            req.setTitle("  Java  ");
            when(bookMapper.countBySearch(any())).thenReturn(0);
            when(bookMapper.selectListBySearch(any(), anyInt(), anyInt())).thenReturn(List.of());

            bookService.search(req);

            assertThat(req.getTitle()).isEqualTo("Java");
        }

        @Test
        @DisplayName("title=\"\" 归一化为 null")
        void search_blankTitleBecomesNull() {
            BookSearchRequest req = new BookSearchRequest();
            req.setTitle("");
            when(bookMapper.countBySearch(any())).thenReturn(0);
            when(bookMapper.selectListBySearch(any(), anyInt(), anyInt())).thenReturn(List.of());

            bookService.search(req);

            assertThat(req.getTitle()).isNull();
        }
    }

    // ============== 粒度区间搜索 ==============

    @Nested
    @DisplayName("粒度区间搜索(/by 系列)")
    class GranularitySearch {

        @Test
        @DisplayName("searchByPublishedDateBy year-only → [2024-01-01, 2025-01-01)")
        void searchByPublishedDateBy_yearOnly() {
            BookPublishedDateByRequest req = new BookPublishedDateByRequest();
            req.setYear(2024);
            ArgumentCaptor<LocalDate> startCap = ArgumentCaptor.forClass(LocalDate.class);
            ArgumentCaptor<LocalDate> endCap = ArgumentCaptor.forClass(LocalDate.class);
            when(bookMapper.countByPublishedDateRange(startCap.capture(), endCap.capture())).thenReturn(0);
            when(bookMapper.selectListByPublishedDateRange(
                    startCap.capture(), endCap.capture(), anyInt(), anyInt()))
                .thenReturn(List.of());

            bookService.searchByPublishedDateBy(req);

            // first capture pair = count; second pair = list — both 应当一致
            assertThat(startCap.getAllValues()).allMatch(d -> d.equals(LocalDate.of(2024, 1, 1)));
            assertThat(endCap.getAllValues()).allMatch(d -> d.equals(LocalDate.of(2025, 1, 1)));
        }

        @Test
        @DisplayName("searchByPublishedDateBy year+month → [year-month-01, year-month+1-01)")
        void searchByPublishedDateBy_yearMonth() {
            BookPublishedDateByRequest req = new BookPublishedDateByRequest();
            req.setYear(2024);
            req.setMonth(6);
            ArgumentCaptor<LocalDate> startCap = ArgumentCaptor.forClass(LocalDate.class);
            ArgumentCaptor<LocalDate> endCap = ArgumentCaptor.forClass(LocalDate.class);
            when(bookMapper.countByPublishedDateRange(startCap.capture(), endCap.capture())).thenReturn(0);
            when(bookMapper.selectListByPublishedDateRange(
                    startCap.capture(), endCap.capture(), anyInt(), anyInt()))
                .thenReturn(List.of());

            bookService.searchByPublishedDateBy(req);

            assertThat(startCap.getAllValues()).allMatch(d -> d.equals(LocalDate.of(2024, 6, 1)));
            assertThat(endCap.getAllValues()).allMatch(d -> d.equals(LocalDate.of(2024, 7, 1)));
        }

        @Test
        @DisplayName("searchByPublishedDateBy year=2024,month=12 → 跨年:end=2025-01-01")
        void searchByPublishedDateBy_yearMonthDecemberCrossesYear() {
            BookPublishedDateByRequest req = new BookPublishedDateByRequest();
            req.setYear(2024);
            req.setMonth(12);
            ArgumentCaptor<LocalDate> endCap = ArgumentCaptor.forClass(LocalDate.class);
            when(bookMapper.countByPublishedDateRange(any(LocalDate.class), endCap.capture())).thenReturn(0);
            when(bookMapper.selectListByPublishedDateRange(
                    any(LocalDate.class), endCap.capture(), anyInt(), anyInt()))
                .thenReturn(List.of());

            bookService.searchByPublishedDateBy(req);

            assertThat(endCap.getValue()).isEqualTo(LocalDate.of(2025, 1, 1));
        }

        @Test
        @DisplayName("searchByPublishedDateBy year+month+day → [day, day+1)")
        void searchByPublishedDateBy_yearMonthDay() {
            BookPublishedDateByRequest req = new BookPublishedDateByRequest();
            req.setYear(2024);
            req.setMonth(2);
            req.setDay(28);  // 闰年 2 月 28 日 → end=29 日
            ArgumentCaptor<LocalDate> endCap = ArgumentCaptor.forClass(LocalDate.class);
            when(bookMapper.countByPublishedDateRange(any(LocalDate.class), endCap.capture())).thenReturn(0);
            when(bookMapper.selectListByPublishedDateRange(
                    any(LocalDate.class), endCap.capture(), anyInt(), anyInt()))
                .thenReturn(List.of());

            bookService.searchByPublishedDateBy(req);

            assertThat(endCap.getValue()).isEqualTo(LocalDate.of(2024, 2, 29));
        }

        @Test
        @DisplayName("粒度跳级:year=null 但 month=6 → compact 抛 BusinessException,Service 不调 mapper")
        void searchByPublishedDateBy_granularitySkipThrows() {
            BookPublishedDateByRequest req = new BookPublishedDateByRequest();
            req.setMonth(6);

            assertThatThrownBy(() -> bookService.searchByPublishedDateBy(req))
                .isInstanceOf(BusinessException.class);

            verify(bookMapper, never()).countByPublishedDateRange(any(LocalDate.class), any(LocalDate.class));
        }

        @Test
        @DisplayName("searchByCreatedTimeBy year-only → [year-01-01, year+1-01-01)")
        void searchByCreatedTimeBy_yearOnly() {
            BookDateTimeByRequest req = new BookDateTimeByRequest();
            req.setYear(2024);
            ArgumentCaptor<LocalDateTime> startCap = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<LocalDateTime> endCap = ArgumentCaptor.forClass(LocalDateTime.class);
            when(bookMapper.countByCreatedTimeRange(startCap.capture(), endCap.capture())).thenReturn(0);
            when(bookMapper.selectListByCreatedTimeRange(
                    startCap.capture(), endCap.capture(), anyInt(), anyInt()))
                .thenReturn(List.of());

            bookService.searchByCreatedTimeBy(req);

            assertThat(startCap.getValue()).isEqualTo(LocalDateTime.of(2024, 1, 1, 0, 0, 0));
            assertThat(endCap.getValue()).isEqualTo(LocalDateTime.of(2025, 1, 1, 0, 0, 0));
        }

        @Test
        @DisplayName("searchByCreatedTimeBy year+month+day+hour+minute → [+1min)")
        void searchByCreatedTimeBy_minute() {
            BookDateTimeByRequest req = new BookDateTimeByRequest();
            req.setYear(2024);
            req.setMonth(6);
            req.setDay(15);
            req.setHour(10);
            req.setMinute(30);
            ArgumentCaptor<LocalDateTime> startCap = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<LocalDateTime> endCap = ArgumentCaptor.forClass(LocalDateTime.class);
            when(bookMapper.countByCreatedTimeRange(startCap.capture(), endCap.capture())).thenReturn(0);
            when(bookMapper.selectListByCreatedTimeRange(
                    startCap.capture(), endCap.capture(), anyInt(), anyInt()))
                .thenReturn(List.of());

            bookService.searchByCreatedTimeBy(req);

            assertThat(startCap.getValue()).isEqualTo(LocalDateTime.of(2024, 6, 15, 10, 30, 0));
            assertThat(endCap.getValue()).isEqualTo(LocalDateTime.of(2024, 6, 15, 10, 31, 0));
        }

        @Test
        @DisplayName("searchByCreatedTimeBy year+month+day+hour → [+1hour)")
        void searchByCreatedTimeBy_hour() {
            BookDateTimeByRequest req = new BookDateTimeByRequest();
            req.setYear(2024);
            req.setMonth(6);
            req.setDay(15);
            req.setHour(10);
            ArgumentCaptor<LocalDateTime> endCap = ArgumentCaptor.forClass(LocalDateTime.class);
            when(bookMapper.countByCreatedTimeRange(any(LocalDateTime.class), endCap.capture())).thenReturn(0);
            when(bookMapper.selectListByCreatedTimeRange(
                    any(LocalDateTime.class), endCap.capture(), anyInt(), anyInt()))
                .thenReturn(List.of());

            bookService.searchByCreatedTimeBy(req);

            assertThat(endCap.getValue()).isEqualTo(LocalDateTime.of(2024, 6, 15, 11, 0, 0));
        }

        @Test
        @DisplayName("searchByUpdatedTimeBy year+month → [+1month)")
        void searchByUpdatedTimeBy_month() {
            BookDateTimeByRequest req = new BookDateTimeByRequest();
            req.setYear(2024);
            req.setMonth(6);
            ArgumentCaptor<LocalDateTime> endCap = ArgumentCaptor.forClass(LocalDateTime.class);
            when(bookMapper.countByUpdatedTimeRange(any(LocalDateTime.class), endCap.capture())).thenReturn(0);
            when(bookMapper.selectListByUpdatedTimeRange(
                    any(LocalDateTime.class), endCap.capture(), anyInt(), anyInt()))
                .thenReturn(List.of());

            bookService.searchByUpdatedTimeBy(req);

            assertThat(endCap.getValue()).isEqualTo(LocalDateTime.of(2024, 7, 1, 0, 0, 0));
        }
    }
}
