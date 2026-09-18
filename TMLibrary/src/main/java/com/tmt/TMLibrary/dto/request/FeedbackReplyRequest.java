package com.tmt.TMLibrary.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 反馈回复 / 追述 —— POST /api/feedbacks/{id}/reply。
 *
 * <p>提交人和管理员共用这个 DTO,差异在 {@code isInternal} 字段:
 * 普通用户传 {@code false};管理员可以传 {@code true} 写"内部备注"(对提交人不可见)。</p>
 */
@Data
public class FeedbackReplyRequest {

    @NotBlank(message = "回复内容不能为空")
    @Size(max = 5000, message = "回复内容长度不能超过 5000 个字符")
    private String body;

    /**
     * 是否内部备注(仅管理员可见) —— 普通用户传过来的值在 controller 内会被无视,
     * 防止有人在请求体里塞个 true 假装内部备注。
     */
    private Boolean isInternal = false;
}
