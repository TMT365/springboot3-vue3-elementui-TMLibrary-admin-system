package com.tmt.TMLibrary.exception;

import com.tmt.TMLibrary.common.Result.Result;
import com.tmt.TMLibrary.common.Result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.beans.factory.annotation.Value;

import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    // 这里可以定义全局异常处理逻辑，例如捕获特定异常并返回自定义的错误响应。
    // 你可以使用 @ControllerAdvice 和 @ExceptionHandler 注解来实现全局异常处理。

    // 匹配 MySQL 报错信息里的字段名: Duplicate entry 'xxx' for key 'book.isbn'
    private static final Pattern DUPLICATE_KEY_PATTERN =
            Pattern.compile("for key '[^.]+\\.([^']+)'");
    /**
     * @brief 处理业务异常
     * 业务异常是指程序逻辑上可以预见的异常情况，例如用户输入错误、资源不存在等。通过捕获业务异常，可以向前端返回明确的错误
     * @param e
     * @return
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常: code={}, msg={}]]], occurrence in {}", e.getCode(), e.getMessage(), e.getWhere());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 处理唯一约束冲突 (例如重复 ISBN)。MySQL 抛出
     * SQLIntegrityConstraintViolationException，被 Spring 包装为 DuplicateKeyException，
     * 转 409 而不是 500，让前端能区分"业务冲突"和"系统异常"。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e) {
        String field = extractConflictField(e.getMessage());
        log.warn("唯一约束冲突: field={}, raw={}", field, e.getMessage());
        return Result.fail(ResultCode.CONFLICT.getCode(),
                "数据已存在,字段 [" + field + "] 重复");
    }

    /**
     * @brief 处理参数校验异常
     * @param e
     * @return
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), msg);
    }

    /**
     * @brief 处理请求体解析异常, 例如 JSON 格式错误:缺胳膊少腿的，或者是多了
     * @param e
     * @return
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        // 完整异常(包括 cause 链里的 Jackson 报错)永远写日志,排查时需要
        log.warn("请求体解析失败", e);
        String msg = "prod".equals(activeProfile) ? "请求体格式错误,请检查 JSON 语法" : "请求体解析失败: " + e.getMessage();
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), msg);
    }

    /**
     * @brief 处理参数类型不匹配异常，例如前端传了字符串给整型参数
     * @param e
     * @return
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e) {
        // 防御:某些边缘场景下 getRequiredType() 可能为 null,直接调用 .getSimpleName() 会 NPE
        Class<?> requiredType = e.getRequiredType();
        String typeName = requiredType != null ? requiredType.getSimpleName() : "未知类型";
        String msg = "参数类型不匹配: 参数名=[" + e.getName()
                + "],期望=[" + typeName
                + "],实际=[" + e.getValue() + "]";
        log.warn("参数类型不匹配: {}", msg);
        String userMsg = "prod".equals(activeProfile) ? "参数类型不匹配" : msg;
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), userMsg);
    }

    /**
     * @brief 处理日期/时间解析异常 — Controller 里 LocalDate.parse / LocalDateTime.parse 失败时抛
     *        之前会被 handleAny 兜底成 500,实际是用户输入问题,应转 400
     * @param e
     * @return
     */
    @ExceptionHandler(DateTimeParseException.class)
    public Result<Void> handleDateTimeParse(DateTimeParseException e) {
        // 解析失败的字符串 + 期望格式提示给前端,方便排查
        String msg = "日期/时间格式错误: 输入=[" + e.getParsedString()
                + "],期望格式=[" + (e.getErrorIndex() >= 0 ? "ISO-8601 (yyyy-MM-dd 或 yyyy-MM-ddTHH:mm:ss)" : "ISO-8601") + "]";
        log.warn("日期/时间解析失败: {}", msg);
        String userMsg = "prod".equals(activeProfile) ? "日期/时间格式错误" : msg;
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), userMsg);
    }

    /**
     * @brief 处理系统异常
     * 系统异常是指程序逻辑上无法预见的异常情况，例如空指针异常、数据库连接失败等。通过捕获系统异常，可以向前端返回通
     * @param e
     * @return
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleAny(Exception e) {
        log.error("系统异常", e);
        String msg = "prod".equals(activeProfile) ? "服务器内部错误" : "服务器内部错误: " + e.getClass().getSimpleName();
        /*
         * 生产环境下不要把异常堆栈信息返回给前端，避免泄露敏感信息。可以在日志中记录详细的异常信息，方便排查问题。
         * 开发环境下可以返回异常类名，方便调试。
        */
        return Result.fail(ResultCode.INTERNAL_ERROR.getCode(), msg);
    }

    /**
     * @brief 从 MySQL DuplicateKeyException 消息里抽出冲突字段名
     * 例如: Duplicate entry '978-7-121-15535-2' for key 'book.isbn'，抽出 isbn. pattern 可以自己通过打印 DuplicateKeyException 的 message 来调试。
     * @param message
     * @return
     */
    /** 从 MySQL DuplicateKeyException 消息里抽出冲突字段名 */
    private String extractConflictField(String message) {
        if (message == null) return "未知字段";
        Matcher m = DUPLICATE_KEY_PATTERN.matcher(message);
        return m.find() ? m.group(1) : "唯一约束字段";
    }
}