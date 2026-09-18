package com.tmt.TMLibrary.dto.response;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 反馈列表里的一行 —— 摘要,不带 {@code body}。
 *
 * <p>列表不打 body 是故意的:候选词(搜索建议)同样的取舍 —— body 可能很大,列表
 * 一次返回 N 条会胖。点进详情才走 {@code GET /{id}} 拿完整 body。</p>
 */
@Data
public class FeedbackSummary {

    private Long id;
    private String title;
    private Integer userId;
    private String username;
    private String category;
    private Integer status;
    private Integer priority;
    /** 是否有至少一条回复 —— 列表里直接显示 chip,不用打开详情才知道 */
    private Boolean hasReply;
    private Integer replyCount;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
