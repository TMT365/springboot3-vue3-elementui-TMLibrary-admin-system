package com.tmt.TMLibrary.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @brief GET /api/books/search/{CreatedTime|UpdatedTime}/by 粒度查询入参
 *
 * 支持 5 级粒度:year / year+month / year+month+day /
 *               year+month+day+hour / year+month+day+hour+minute
 * 必须从大到小连续(粒度连续性由 {@link #compact()} 校验):
 *   - year 必填
 *   - month/day/hour/minute 都可选,但必须连续不能跳级
 *
 * 例:
 *   year=2024                          → 2024 全年
 *   year=2024&month=6                  → 2024-06 全月
 *   year=2024&month=6&day=15           → 2024-06-15 全天
 *   year=2024&month=6&day=15&hour=10   → 2024-06-15 10 时
 *   year=2024&month=6&day=15&hour=10&minute=30 → 2024-06-15 10:30 这一分钟
 *   year=2024&hour=10                  → 400(粒度跳级)
 */
@Data
public class BookDateTimeByRequest {

    @NotNull(message = "year 必填")
    @Min(value = 1900, message = "year 不能小于 1900")
    @Max(value = 2100, message = "year 不能大于 2100")
    private Integer year;

    @Min(value = 1, message = "month 必须在 1-12")
    @Max(value = 12, message = "month 必须在 1-12")
    private Integer month;

    @Min(value = 1, message = "day 必须在 1-31")
    @Max(value = 31, message = "day 必须在 1-31")
    private Integer day;

    @Min(value = 0, message = "hour 必须在 0-23")
    @Max(value = 23, message = "hour 必须在 0-23")
    private Integer hour;

    @Min(value = 0, message = "minute 必须在 0-59")
    @Max(value = 59, message = "minute 必须在 0-59")
    private Integer minute;

    private Integer page;
    private Integer size;

    /**
     * @brief 归一化 + 粒度连续性校验。
     *        失败抛 {@link com.tmt.TMLibrary.exception.BusinessException}
     *        → GlobalExceptionHandler 转 400。
     * @return this(链式友好)
     */
    public BookDateTimeByRequest compact() {
        // 负数/0 兜底(null 让后续校验接管)
        if (year != null && year <= 0) year = null;
        if (month != null && month <= 0) month = null;
        if (day != null && day <= 0) day = null;
        if (hour != null && hour < 0) hour = null;
        if (minute != null && minute < 0) minute = null;
        // 粒度连续性校验
        if (month != null && year == null) {
            throw new com.tmt.TMLibrary.exception.BusinessException(
                    com.tmt.TMLibrary.common.Result.ResultCode.BAD_REQUEST,
                    "month 必填需 year 先填");
        }
        if (day != null && month == null) {
            throw new com.tmt.TMLibrary.exception.BusinessException(
                    com.tmt.TMLibrary.common.Result.ResultCode.BAD_REQUEST,
                    "day 必填需 month 先填");
        }
        if (hour != null && day == null) {
            throw new com.tmt.TMLibrary.exception.BusinessException(
                    com.tmt.TMLibrary.common.Result.ResultCode.BAD_REQUEST,
                    "hour 必填需 day 先填");
        }
        if (minute != null && hour == null) {
            throw new com.tmt.TMLibrary.exception.BusinessException(
                    com.tmt.TMLibrary.common.Result.ResultCode.BAD_REQUEST,
                    "minute 必填需 hour 先填");
        }
        // page/size 兜底
        this.page = (page == null || page <= 0) ? 1 : page;
        this.size = (size == null || size <= 0) ? 10 : Math.min(size, 100);
        return this;
    }
}
