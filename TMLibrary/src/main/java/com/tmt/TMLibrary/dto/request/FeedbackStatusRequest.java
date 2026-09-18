package com.tmt.TMLibrary.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 改反馈状态/优先级 —— PATCH /api/feedbacks/{id}/status,仅 ADMIN/BOSS 可调。
 *
 * <p>两个字段都允许省略(只改其一也是合法的),但至少要传一个 —— 用 {@code @AssertTrue} 在
 * 字段之间做"至少一个非空"的检查(写在 Service 层,因为 DTO 上跨字段校验要写起来
 * 比较绕且项目里已有用 Service 校验的先例)。</p>
 */
@Data
public class FeedbackStatusRequest {

    @Min(value = 0, message = "status 必须在 0..3 之间")
    @Max(value = 3, message = "status 必须在 0..3 之间")
    private Integer status;

    @Min(value = 0, message = "priority 必须在 0..3 之间")
    @Max(value = 3, message = "priority 必须在 0..3 之间")
    private Integer priority;
}
