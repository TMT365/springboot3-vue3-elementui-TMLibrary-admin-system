package com.tmt.TMLibrary.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @brief GlobalExceptionHandler 测试 — 用 stub controller 触发各类异常
 *
 * 覆盖(对应 {@link GlobalExceptionHandler} 里的 @ExceptionHandler):
 *   - BusinessException → Result.fail(code, msg)
 *   - DuplicateKeyException → 409 + 字段名
 *   - MethodArgumentNotValidException → 400 + 字段级 msg
 *   - HttpMessageNotReadableException → 400
 *   - MethodArgumentTypeMismatchException → 400
 *   - DateTimeParseException → 400 + 期望格式提示
 *   - 其它 Exception → 500
 *
 * 用 @WebMvcTest 加载 stub controller + @Import 加载 advice,
 * 验证的是"真实 HTTP 调用链" + @RestControllerAdvice 拦截 + @ResponseBody 序列化。
 */
@WebMvcTest(StubExceptionController.class)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = "spring.profiles.active=dev")
@DisplayName("GlobalExceptionHandler 全局异常测试")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    // ============== BusinessException ==============

    @Nested
    @DisplayName("BusinessException 业务异常")
    class BusinessExceptionTests {

        @Test
        @DisplayName("BusinessException(NOT_FOUND) → 200 HTTP, body code=404 + msg 含 id")
        void notFound_returns404() throws Exception {
            mockMvc.perform(get("/test/business-not-found"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg").value(containsString("5")));
        }

        @Test
        @DisplayName("BusinessException(BAD_REQUEST) → body code=400")
        void badRequest_returns400() throws Exception {
            mockMvc.perform(get("/test/business-bad-request"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value(containsString("参数错")));
        }
    }

    // ============== DuplicateKeyException ==============

    @Test
    @DisplayName("DuplicateKeyException → body code=409 + msg 含字段名 'isbn'")
    void duplicateKey_returns409WithField() throws Exception {
        mockMvc.perform(get("/test/duplicate-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(409))
            .andExpect(jsonPath("$.msg").value(containsString("isbn")));
    }

    // ============== @Valid 校验失败 ==============

    @Test
    @DisplayName("@Valid 失败(缺 title) → body code=400 + msg 含 'title'")
    void validationFails_returns400WithField() throws Exception {
        String json = "{"
            + "\"author\":\"x\","
            + "\"isbn\":\"1234567890\","
            + "\"price\":10.00,"
            + "\"stockQuantity\":1,"
            + "\"publishedDate\":\"2024-01-01\""
            + "}";

        mockMvc.perform(post("/test/validation-fail")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.msg").value(containsString("title")));
    }

    // ============== JSON 解析失败 ==============

    @Test
    @DisplayName("JSON 语法错 → body code=400")
    void jsonMalformed_returns400() throws Exception {
        mockMvc.perform(post("/test/json-malformed")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ this is not json"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400));
    }

    // ============== DateTimeParseException ==============

    @Test
    @DisplayName("DateTimeParseException → body code=400 + msg 含 'not-a-date'")
    void dateTimeParse_returns400WithInput() throws Exception {
        mockMvc.perform(get("/test/date-time-parse").param("d", "not-a-date"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.msg").value(containsString("not-a-date")));
    }

    // ============== Type Mismatch ==============

    @Test
    @DisplayName("参数类型不匹配(传 'abc' 给 int)→ body code=400")
    void typeMismatch_returns400() throws Exception {
        mockMvc.perform(get("/test/type-mismatch").param("n", "abc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.msg").value(containsString("n")));
    }

    // ============== 通用 Exception ==============

    @Test
    @DisplayName("未捕获的 RuntimeException → body code=500")
    void unhandledException_returns500() throws Exception {
        mockMvc.perform(get("/test/runtime-exception"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(500));
    }
}
