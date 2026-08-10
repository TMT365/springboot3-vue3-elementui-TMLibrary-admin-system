package com.tmt.TMLibrary.exception;

import com.tmt.TMLibrary.common.Result.ResultCode;
import com.tmt.TMLibrary.dto.BookSaveRequest;
import com.tmt.TMLibrary.entity.Book;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.format.DateTimeParseException;

/**
 * @brief 测试用 stub controller — 触发各种异常供 GlobalExceptionHandler 测试使用
 *
 * 必须放在顶层(不能嵌套在测试类里),否则 @WebMvcTest 不会扫描到。
 */
@RestController
@RequestMapping("/test")
public class StubExceptionController {

    @GetMapping("/business-not-found")
    public Book businessNotFound() {
        throw new BusinessException(ResultCode.NOT_FOUND, "图书不存在, id=5");
    }

    @GetMapping("/business-bad-request")
    public Book businessBadRequest() {
        throw new BusinessException(ResultCode.BAD_REQUEST, "参数错");
    }

    @GetMapping("/duplicate-key")
    public Book duplicateKey() {
        // 模拟 MySQL 唯一约束冲突的 message 格式
        throw new org.springframework.dao.DuplicateKeyException(
            "Duplicate entry '9780134685991' for key 'book.isbn'");
    }

    @GetMapping("/date-time-parse")
    public Book dateTimeParse(@RequestParam("d") String d) {
        throw new DateTimeParseException("not-a-date", d, 0);
    }

    @GetMapping("/type-mismatch")
    public Book typeMismatch(@RequestParam("n") int n) {
        return new Book();
    }

    @GetMapping("/runtime-exception")
    public Book runtimeException() {
        throw new RuntimeException("boom");
    }

    @PostMapping("/validation-fail")
    public String validationFail(@jakarta.validation.Valid @RequestBody BookSaveRequest req) {
        return "ok";
    }

    @PostMapping("/json-malformed")
    public String jsonMalformed(@RequestBody BookSaveRequest req) {
        return "ok";
    }
}
