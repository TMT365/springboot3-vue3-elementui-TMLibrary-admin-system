package com.tmt.TMLibrary.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 提交反馈 —— POST /feedbacks/mine。
 *
 * <p>校验与 SQL 的 {@code category VARCHAR(32) / title VARCHAR(120) / body TEXT} 对齐 —— 前后端
 * 都把字段长度卡住,避免垃圾数据(尤其 title,一条反馈标题写一整页没意义)。</p>
 */
@Data
public class FeedbackCreateRequest {

    /** 反馈分类 —— 后端白名单(防止前端被改/新前端发奇怪的 category 进来污染统计) */
    @NotBlank(message = "分类不能为空")
    @Pattern(
            regexp = "BUG|FEATURE|QUESTION|OTHER",
            message = "分类必须是 BUG / FEATURE / QUESTION / OTHER 之一"
    )
    private String category;

    @NotBlank(message = "标题不能为空")
    @Size(max = 120, message = "标题长度不能超过 120 个字符")
    private String title;

    @NotBlank(message = "描述不能为空")
    @Size(max = 5000, message = "描述长度不能超过 5000 个字符")
    private String body;
}
