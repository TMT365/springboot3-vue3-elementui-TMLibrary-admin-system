package com.tmt.TMLibrary.dto.response;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 单条回复(详情页用)。
 */
@Data
public class FeedbackReplyView {

    private Long id;
    private Integer userId;
    private String username;
    private Integer role;
    /** 仅内部备注字段:前端用它决定是否展示。普通用户拿到的回复列表里这个值永远是 0 */
    private Integer isInternal;
    private String body;
    private LocalDateTime createdTime;
}
