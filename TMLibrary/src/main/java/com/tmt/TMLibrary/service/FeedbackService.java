package com.tmt.TMLibrary.service;

import com.tmt.TMLibrary.common.Result.PageResult;
import com.tmt.TMLibrary.dto.request.FeedbackCreateRequest;
import com.tmt.TMLibrary.dto.request.FeedbackReplyRequest;
import com.tmt.TMLibrary.dto.request.FeedbackStatusRequest;
import com.tmt.TMLibrary.dto.response.FeedbackView;
import com.tmt.TMLibrary.security.context.UserView;

/**
 * 反馈工单 Service 接口。
 *
 * <p>管理员权限校验放在 Controller 层(模仿 {@code StatsController} 的做法),
 * Service 不重复判断角色 —— 越早判断越易"做错位置"。</p>
 */
public interface FeedbackService {

    /** 用户提交反馈 —— 写主表,user_id 从 me 取,初始 status=OPEN priority=NORMAL */
    Long create(UserView me, FeedbackCreateRequest req);

    /**
     * 详情 —— 按当前用户角色过滤回复:
     * <ul>
     *   <li>本工单提交人 → 看得到所有人的非内部回复</li>
     *   <li>ADMIN/BOSS → 看得到所有回复(含内部备注)</li>
     *   <li>其他用户 → 403</li>
     * </ul>
     */
    FeedbackView detail(UserView me, Long id);

    /** 我的反馈列表(只看自己) */
    PageResult<?> myList(UserView me, int page, int size);

    /** 全员反馈列表(ADMIN/BOSS),支持 status / category 过滤 */
    PageResult<?> allList(Integer status, String category, int page, int size);

    /** 改状态/优先级 —— 校验两个字段至少传一个 */
    void changeStatus(UserView me, Long id, FeedbackStatusRequest req);

    /** 追述 / 回复 —— 普通用户只能给"自己提的"追述,管理员/BOSS 可以给任何反馈回复 */
    void reply(UserView me, Long id, FeedbackReplyRequest req);
}
