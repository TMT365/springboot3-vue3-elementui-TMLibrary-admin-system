package com.tmt.TMLibrary.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 反馈回复 / 追述。
 *
 * <p>一张表装两类内容:</p>
 * <ul>
 *   <li>用户的"我补一句":role=0(USER),is_internal=0</li>
 *   <li>管理员的回复:role=1(ADMIN) 或 2(BOSS),is_internal=0</li>
 *   <li>管理员的内部备注:role=1/2,is_internal=1 —— 提交人看不到</li>
 * </ul>
 */
@Data
public class FeedbackReply {

    private Long id;
    private Long feedbackId;
    private Integer userId;
    private Integer role;
    private Integer isInternal;
    private String body;
    private LocalDateTime createdTime;
}
