package com.tmt.TMLibrary.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @brief GET /api/books/search/publishedDate/by 粒度查询入参
 *
 * 支持 3 级粒度:year / year+month / year+month+day
 * 必须从大到小连续(粒度连续性由 {@link #compact()} 校验):
 *   - year 必填
 *   - month 可选,但传了 month 必须传 year(year 本就必填)
 *   - day 可选,但传了 day 必须传 month
 *
 * 例:
 *   year=2024                  → 2024 全年
 *   year=2024&month=6          → 2024-06 全月
 *   year=2024&month=6&day=15   → 2024-06-15 全天
 *   year=2024&day=15           → 400(粒度跳级)
 *   month=6                    → 400(缺 year)
 */
@Data
public class BookPublishedDateByRequest {

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

    private Integer page;
    private Integer size;

    /**
     * @brief 归一化 + 粒度连续性校验。
     *        失败抛 {@link com.tmt.TMLibrary.exception.BusinessException}
     *        → GlobalExceptionHandler 转 400。
     * @return this(链式友好)
     */
    public BookPublishedDateByRequest compact() {
        // year ≤ 0 视同未传(让 @NotNull 接管给 400)
        if (year != null && year <= 0) year = null;
        // month/day 负数同样兜底
        if (month != null && month <= 0) month = null;
        if (day != null && day <= 0) day = null;
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
        // page/size 兜底(同 BookSearchRequest)
        this.page = (page == null || page <= 0) ? 1 : page;
        this.size = (size == null || size <= 0) ? 10 : Math.min(size, 100);
        return this;
    }
}
