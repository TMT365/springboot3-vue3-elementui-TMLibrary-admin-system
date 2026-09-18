package com.tmt.TMLibrary.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 反馈工单主表。
 *
 * <h2>状态字段</h2>
 * <p>{@code status} 是 {@link Integer} 而不是枚举类型 —— 模仿本项目 {@code Order.orderStatus}
 * 的做法:数据库里它就是 TINYINT,DTO 不翻译成枚举(JS 端只接 number);
 * 业务层"翻译"集中在 {@code FeedbackController} 的 status 校验和列表的 chip 渲染里。</p>
 *
 * <p>状态码(整型):0=OPEN 1=IN_PROGRESS 2=RESOLVED 3=CLOSED。流转规则:
 * OPEN → IN_PROGRESS → RESOLVED(记 resolved_time)/CLOSED。RESOLVED 是"已修好但还开着等用户确认",
 * CLOSED 是"用户也认了",这两个在管理后台都是绿。</p>
 */
@Data
public class Feedback {

    private Long id;
    private Integer userId;
    private String category;
    private String title;
    private String body;
    private Integer status;
    private Integer priority;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    private LocalDateTime resolvedTime;
}
