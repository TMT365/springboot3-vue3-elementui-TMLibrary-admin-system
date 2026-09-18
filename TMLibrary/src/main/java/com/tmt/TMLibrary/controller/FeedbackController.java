package com.tmt.TMLibrary.controller;

import com.tmt.TMLibrary.common.Result.Result;
import com.tmt.TMLibrary.common.User.UserRole;
import com.tmt.TMLibrary.dto.request.FeedbackCreateRequest;
import com.tmt.TMLibrary.dto.request.FeedbackReplyRequest;
import com.tmt.TMLibrary.dto.request.FeedbackStatusRequest;
import com.tmt.TMLibrary.dto.response.FeedbackSummary;
import com.tmt.TMLibrary.dto.response.FeedbackView;
import com.tmt.TMLibrary.exception.BusinessException;
import com.tmt.TMLibrary.common.Result.ResultCode;
import com.tmt.TMLibrary.common.Result.PageResult;
import com.tmt.TMLibrary.security.context.CurrentUser;
import com.tmt.TMLibrary.security.context.UserView;
import com.tmt.TMLibrary.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 反馈工单 Controller。
 *
 * <p>所有路径都在 {@code /api/feedbacks/**} 下 —— JwtAuthFilter 没在白名单里,
 * 所以全部要求登录(这正是题目要求)。管理员权限(改状态、查所有)在校验里做,
 * 不在过滤器里做 —— 跟 {@code StatsController} 一个套路。</p>
 */
@RestController
@RequestMapping("/api/feedbacks")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    /** 提交反馈 —— 任何登录用户 */
    @PostMapping("/mine")
    public Result<Long> create(@CurrentUser UserView me,
                               @Valid @RequestBody FeedbackCreateRequest req) {
        return Result.success(feedbackService.create(me, req));
    }

    /** 我的反馈列表 */
    @GetMapping("/mine")
    public Result<PageResult<FeedbackSummary>> myList(@CurrentUser UserView me,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        @SuppressWarnings("unchecked")
        PageResult<FeedbackSummary> result = (PageResult<FeedbackSummary>) feedbackService.myList(me, page, size);
        return Result.success(result);
    }

    /** 全部反馈(管理员/老板) —— 支持 status / category 过滤 */
    @GetMapping("/all")
    public Result<PageResult<FeedbackSummary>> allList(@CurrentUser UserView me,
                                                      @RequestParam(required = false) Integer status,
                                                      @RequestParam(required = false) String category,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        requireAdminOrBoss(me);
        @SuppressWarnings("unchecked")
        PageResult<FeedbackSummary> result = (PageResult<FeedbackSummary>) feedbackService.allList(status, category, page, size);
        return Result.success(result);
    }

    /** 详情 —— 提交人本人或管理员/BOSS */
    @GetMapping("/{id}")
    public Result<FeedbackView> detail(@CurrentUser UserView me, @PathVariable Long id) {
        return Result.success(feedbackService.detail(me, id));
    }

    /** 追述 / 回复 —— 提交人自己 / 管理员 / 老板 */
    @PostMapping("/{id}/reply")
    public Result<Void> reply(@CurrentUser UserView me,
                              @PathVariable Long id,
                              @Valid @RequestBody FeedbackReplyRequest req) {
        feedbackService.reply(me, id, req);
        return Result.success();
    }

    /** 改状态/优先级 —— 仅管理员 / 老板 */
    @PatchMapping("/{id}/status")
    public Result<Void> changeStatus(@CurrentUser UserView me,
                                    @PathVariable Long id,
                                    @Valid @RequestBody FeedbackStatusRequest req) {
        feedbackService.changeStatus(me, id, req);
        return Result.success();
    }

    /**
     * 角色校验 —— 跟 {@code StatsController} 同款,把 role code 翻译出来再比。
     * Service 内部也会有一次校验(双保险),但 Controller 这一层抛 403 能让
     * 错误响应走 GlobalExceptionHandler 的统一格式,而不是服务层那个 500。
     */
    private static void requireAdminOrBoss(UserView me) {
        if (me == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录");
        }
        Integer role = me.getRole();
        if (!UserRole.ADMIN.getCode().equals(role) && !UserRole.BOSS.getCode().equals(role)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可访问");
        }
    }
}
