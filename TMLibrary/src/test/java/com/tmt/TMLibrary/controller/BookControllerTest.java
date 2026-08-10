package com.tmt.TMLibrary.controller;

// Spring Boot 4.x 迁移说明:
//   - {@code @WebMvcTest}    移到 {@code org.springframework.boot.webmvc.test.autoconfigure}
//   - {@code @MockBean}      改名为 {@code @MockitoBean} (Spring Framework 7 / Spring Boot 4.x 移除旧 API)
//   - Jackson 3.x 改用包前缀 {@code tools.jackson.*} (取代 {@code com.fasterxml.jackson.*})

import com.tmt.TMLibrary.common.Result.PageResult;
import com.tmt.TMLibrary.common.Result.ResultCode;
import com.tmt.TMLibrary.dto.BookSaveRequest;
import com.tmt.TMLibrary.entity.Book;
import com.tmt.TMLibrary.exception.BusinessException;
import com.tmt.TMLibrary.exception.GlobalExceptionHandler;
import com.tmt.TMLibrary.service.BookService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @brief BookController HTTP 切片测试 — {@code @WebMvcTest} + MockMvc
 *
 * 覆盖:
 *   - GET 分页:GET /api/books/list
 *   - GET 单条:GET /api/books/display
 *   - POST 创建:POST /api/books/created(走 @Valid)
 *   - PATCH 更新:PATCH /api/books/updated
 *   - DELETE 删除:DELETE /api/books/deleted/id/{id}
 *   - DELETE by ISBN:DELETE /api/books/deleted/isbn/{isbn}
 *   - GET by ISBN:GET /api/books/display/isbn/{isbn}
 *   - 多条件搜索:GET /api/books(走 @ModelAttribute)
 *   - 粒度查询:GET /api/books/search/{publishedDate|CreatedTime|UpdatedTime}/by
 *   - @Valid 失败 → 400 + Result 包
 *   - BusinessException → 404 + Result 包
 *   - DateTimeParseException → 400 + Result 包
 */
@WebMvcTest(BookController.class)
@Import(GlobalExceptionHandler.class)    // 必须显式 import,@WebMvcTest 默认不扫 @RestControllerAdvice
@TestPropertySource(properties = "spring.profiles.active=dev")
@DisplayName("BookController HTTP 测试")
class BookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BookService bookService;

    private static Book newBook(int id) {
        Book b = new Book();
        b.setId(id);
        b.setTitle("Effective Java");
        b.setAuthor("Joshua Bloch");
        b.setIsbn("9780134685991");
        b.setPrice(new BigDecimal("42.00"));
        b.setStockQuantity(100);
        b.setPublishedDate(LocalDate.of(2018, 1, 6));
        return b;
    }

    // ============== GET /api/books/list ==============

    @Nested
    @DisplayName("GET /api/books/list 分页")
    class List {

        @Test
        @DisplayName("默认参数 page=1,size=10 → 200 + Result 包")
        void list_default_returnsEnvelope() throws Exception {
            when(bookService.page(1, 10)).thenReturn(new PageResult<>(1L, java.util.List.of(newBook(1))));

            mockMvc.perform(get("/api/books/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("成功"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.data[0].title").value("Effective Java"));
        }

        @Test
        @DisplayName("自定义 page=2,size=5 → 传入 Service")
        void list_customPageAndSize_passedToService() throws Exception {
            when(bookService.page(2, 5)).thenReturn(new PageResult<>(0L, java.util.List.of()));

            mockMvc.perform(get("/api/books/list").param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

            verify(bookService).page(2, 5);
        }
    }

    // ============== GET /api/books/display ==============

    @Nested
    @DisplayName("GET /api/books/display 按 ID")
    class Display {

        @Test
        @DisplayName("display?id=1 → 200 + Book")
        void display_found() throws Exception {
            when(bookService.getById(1)).thenReturn(newBook(1));

            mockMvc.perform(get("/api/books/display").param("id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").value("Effective Java"));
        }

        @Test
        @DisplayName("display 找不到 → 200 HTTP, 但 body code=404 + msg 含 id")
        void display_notFound_returnsNotFoundEnvelope() throws Exception {
            when(bookService.getById(999))
                .thenThrow(new BusinessException(ResultCode.NOT_FOUND, "图书不存在, id=999"));

            mockMvc.perform(get("/api/books/display").param("id", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg").value(containsString("999")));
        }
    }

    // ============== POST /api/books/created ==============

    @Nested
    @DisplayName("POST /api/books/created 创建")
    class Created {

        @Test
        @DisplayName("合法 JSON → 200 + code=200")
        void create_valid_returnsSuccess() throws Exception {
            BookSaveRequest req = new BookSaveRequest();
            req.setTitle("Effective Java");
            req.setAuthor("Joshua Bloch");
            req.setIsbn("9780134685991");
            req.setPrice(new BigDecimal("42.00"));
            req.setStockQuantity(100);
            req.setPublishedDate(LocalDate.of(2018, 1, 6));

            mockMvc.perform(post("/api/books/created")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

            verify(bookService).create(any(BookSaveRequest.class));
        }

        @Test
        @DisplayName("缺 title → 200 HTTP, body code=400 + msg 含 'title'")
        void create_missingTitle_returns400() throws Exception {
            String json = "{"
                + "\"author\":\"x\","
                + "\"isbn\":\"1234567890\","
                + "\"price\":10.00,"
                + "\"stockQuantity\":1,"
                + "\"publishedDate\":\"2024-01-01\""
                + "}";

            mockMvc.perform(post("/api/books/created")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value(containsString("title")));

            verify(bookService, never()).create(any());
        }

        @Test
        @DisplayName("ISBN 格式非法(全字母) → 400 + msg 含 'isbn'")
        void create_invalidIsbn_returns400() throws Exception {
            String json = "{"
                + "\"title\":\"X\","
                + "\"author\":\"Y\","
                + "\"isbn\":\"abcdefghij\","
                + "\"price\":10.00,"
                + "\"stockQuantity\":1,"
                + "\"publishedDate\":\"2024-01-01\""
                + "}";

            mockMvc.perform(post("/api/books/created")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value(containsString("ISBN")));
        }

        @Test
        @DisplayName("JSON 语法错(缺右大括号) → 400 + msg 含 'JSON'")
        void create_malformedJson_returns400() throws Exception {
            String malformed = "{\"title\":\"x\"";  // 缺 }

            mockMvc.perform(post("/api/books/created")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(malformed))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
        }
    }

    // ============== PATCH /api/books/updated ==============

    @Nested
    @DisplayName("PATCH /api/books/updated 按 ID 更新")
    class Updated {

        @Test
        @DisplayName("合法请求 → 200 + code=200,Service.updateById 被调")
        void update_valid() throws Exception {
            BookSaveRequest req = new BookSaveRequest();
            req.setTitle("New Title");
            req.setAuthor("New Author");
            req.setIsbn("9780134685991");
            req.setPrice(new BigDecimal("50.00"));
            req.setStockQuantity(200);
            req.setPublishedDate(LocalDate.of(2020, 1, 1));

            mockMvc.perform(patch("/api/books/updated")
                    .param("id", "1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

            verify(bookService).updateById(eq(1), any(BookSaveRequest.class));
        }

        @Test
        @DisplayName("Service 抛 NotFound → 200 HTTP, body code=404")
        void update_notFound_returns404() throws Exception {
            BookSaveRequest req = new BookSaveRequest();
            req.setTitle("T");
            req.setAuthor("A");
            req.setIsbn("9780134685991");
            req.setPrice(new BigDecimal("10.00"));
            req.setStockQuantity(1);
            req.setPublishedDate(LocalDate.of(2024, 1, 1));

            doThrow(new BusinessException(ResultCode.NOT_FOUND, "图书不存在, id=1"))
                .when(bookService).updateById(eq(1), any(BookSaveRequest.class));

            mockMvc.perform(patch("/api/books/updated")
                    .param("id", "1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg").value(containsString("1")));
        }
    }

    // ============== DELETE /api/books/deleted/id/{id} ==============

    @Nested
    @DisplayName("DELETE 删除")
    class Delete {

        @Test
        @DisplayName("DELETE by id → 200")
        void deleteById_success() throws Exception {
            mockMvc.perform(delete("/api/books/deleted/id/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

            verify(bookService).deleteById(1);
        }

        @Test
        @DisplayName("DELETE by id 找不到 → 200 HTTP, body code=404")
        void deleteById_notFound() throws Exception {
            doThrow(new BusinessException(ResultCode.NOT_FOUND, "图书不存在, id=999"))
                .when(bookService).deleteById(999);

            mockMvc.perform(delete("/api/books/deleted/id/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
        }

        @Test
        @DisplayName("DELETE by ISBN → 200")
        void deleteByIsbn_success() throws Exception {
            mockMvc.perform(delete("/api/books/deleted/isbn/9780134685991"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

            verify(bookService).deleteByISBN("9780134685991");
        }
    }

    // ============== PATCH /api/books/updated/isbn/{isbn} ==============

    @Test
    @DisplayName("PATCH /api/books/updated/isbn/{isbn} 合法请求 → 200")
    void updateByIsbn_valid() throws Exception {
        BookSaveRequest req = new BookSaveRequest();
        req.setTitle("T");
        req.setAuthor("A");
        req.setIsbn("9780134685991");
        req.setPrice(new BigDecimal("10.00"));
        req.setStockQuantity(1);
        req.setPublishedDate(LocalDate.of(2024, 1, 1));

        mockMvc.perform(patch("/api/books/updated/isbn/9780134685991")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(bookService).updateByISBN(eq("9780134685991"), any(BookSaveRequest.class));
    }

    // ============== GET /api/books/display/isbn/{isbn} ==============

    @Test
    @DisplayName("GET /api/books/display/isbn/{isbn} 找到 → 200 + Book")
    void getByIsbn_found() throws Exception {
        when(bookService.getByISBN("9780134685991")).thenReturn(newBook(1));

        mockMvc.perform(get("/api/books/display/isbn/9780134685991"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.isbn").value("9780134685991"));
    }

    // ============== GET /api/books/search/publishedDate/{date} ==============

    @Nested
    @DisplayName("GET /api/books/search/publishedDate/{date}")
    class SearchByPublishedDate {

        @Test
        @DisplayName("合法日期 → 200,Service 收到正确 LocalDate")
        void validDate() throws Exception {
            when(bookService.searchByPublishedDate(eq(LocalDate.of(2024, 1, 1)), eq(1), eq(10)))
                .thenReturn(new PageResult<>(0L, java.util.List.of()));

            mockMvc.perform(get("/api/books/search/publishedDate/2024-01-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

            verify(bookService).searchByPublishedDate(LocalDate.of(2024, 1, 1), 1, 10);
        }

        @Test
        @DisplayName("非法日期 'not-a-date' → 400 + DateTimeParse msg")
        void invalidDate_returns400() throws Exception {
            mockMvc.perform(get("/api/books/search/publishedDate/not-a-date"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value(containsString("not-a-date")));
        }
    }

    // ============== GET /api/books(多条件搜索) ==============

    @Nested
    @DisplayName("GET /api/books 多条件搜索(@ModelAttribute)")
    class MultiSearch {

        @Test
        @DisplayName("空参数 → 200,Service 收到空 DTO")
        void emptyParams() throws Exception {
            when(bookService.search(any())).thenReturn(new PageResult<>(0L, java.util.List.of()));

            mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

            verify(bookService).search(any());
        }

        @Test
        @DisplayName("title=Java&minPrice=10 → 参数绑定成功")
        void withParams() throws Exception {
            when(bookService.search(any())).thenReturn(new PageResult<>(0L, java.util.List.of()));

            mockMvc.perform(get("/api/books")
                    .param("title", "Java")
                    .param("minPrice", "10"))
                .andExpect(status().isOk());

            verify(bookService).search(any());
        }
    }

    // ============== GET /api/books/search/publishedDate/by ==============

    @Test
    @DisplayName("GET /api/books/search/publishedDate/by?year=2024 → 200,粒度连续性 OK")
    void searchByPublishedDateBy_yearOnly() throws Exception {
        when(bookService.searchByPublishedDateBy(any())).thenReturn(new PageResult<>(0L, java.util.List.of()));

        mockMvc.perform(get("/api/books/search/publishedDate/by").param("year", "2024"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(bookService).searchByPublishedDateBy(any());
    }

    @Test
    @DisplayName("GET /api/books/search/publishedDate/by?month=6 缺 year → 400")
    void searchByPublishedDateBy_monthWithoutYear_returns400() throws Exception {
        // Service 是 mock,但 compact() 在真实 Service 实现里调用 DTO
        // 用 thenAnswer 让 mock 也调一次 compact(),模拟真实调用链
        when(bookService.searchByPublishedDateBy(any())).thenAnswer(inv -> {
            com.tmt.TMLibrary.dto.BookPublishedDateByRequest req =
                inv.getArgument(0);
            req.compact();
            return new PageResult<>(0L, java.util.List.of());
        });

        mockMvc.perform(get("/api/books/search/publishedDate/by").param("month", "6"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.msg").value(containsString("year")));
    }

    // ============== 其它端点 ==============

    @Test
    @DisplayName("GET /api/books/search/title/{title} → 200,Service 收到 title")
    void searchByTitle() throws Exception {
        when(bookService.searchByTitle(eq("Java"), eq(1), eq(10)))
            .thenReturn(new PageResult<>(0L, java.util.List.of()));

        mockMvc.perform(get("/api/books/search/title/Java"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(bookService).searchByTitle("Java", 1, 10);
    }

    @Test
    @DisplayName("GET /api/books/search/PriceRange/10/50 → 200")
    void searchByPriceRange() throws Exception {
        when(bookService.searchByPriceRange(eq(new BigDecimal("10")), eq(new BigDecimal("50")), eq(1), eq(10)))
            .thenReturn(new PageResult<>(0L, java.util.List.of()));

        mockMvc.perform(get("/api/books/search/PriceRange/10/50"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(bookService).searchByPriceRange(new BigDecimal("10"), new BigDecimal("50"), 1, 10);
    }

    @Test
    @DisplayName("GET /api/books/search/StockQuantityRange/5/100 → 200")
    void searchByStockQuantityRange() throws Exception {
        when(bookService.searchByStockQuantityRange(eq(5), eq(100), eq(1), eq(10)))
            .thenReturn(new PageResult<>(0L, java.util.List.of()));

        mockMvc.perform(get("/api/books/search/StockQuantityRange/5/100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(bookService).searchByStockQuantityRange(5, 100, 1, 10);
    }
}
