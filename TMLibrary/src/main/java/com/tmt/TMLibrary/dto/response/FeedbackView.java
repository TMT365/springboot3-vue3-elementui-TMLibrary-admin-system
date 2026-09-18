package com.tmt.TMLibrary.dto.response;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 单条反馈详情 + 回复列表。
 *
 * <p>前端用一次 GET 拿到完整状态(避免再发一个 "查回复列表" 请求);
 * {@code replies} 里 {@code isInternal=true} 的项已经在 Service 层按
 * 当前用户角色过滤过 —— 普通用户拿不到内部备注。</p>
 */
@Data
public class FeedbackView {

    private Long id;
    private Integer userId;
    /** 提交人用户名 —— 联表冗余,前端展示不用再发 /users/{id} */
    private String username;
    private String category;
    private String title;
    private String body;
    private Integer status;
    private Integer priority;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    private LocalDateTime resolvedTime;
    private List<FeedbackReplyView> replies;
}
