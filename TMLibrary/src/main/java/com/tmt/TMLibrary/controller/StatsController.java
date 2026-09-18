package com.tmt.TMLibrary.controller;

import com.tmt.TMLibrary.common.Result.Result;
import com.tmt.TMLibrary.common.Result.ResultCode;
import com.tmt.TMLibrary.common.User.UserRole;
import com.tmt.TMLibrary.dto.response.DashboardStatsResponse;
import com.tmt.TMLibrary.exception.AuthException;
import com.tmt.TMLibrary.exception.BusinessException;
import com.tmt.TMLibrary.security.context.CurrentUser;
import com.tmt.TMLibrary.security.context.UserView;
import com.tmt.TMLibrary.service.StatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统计接口 —— 目前只有仪表盘一个端点。
 *
 * <p><b>鉴权</b>:{@code /stats/**} 不在 {@code JwtAuthFilter} 白名单里,
 * 所以未带 token 的请求会在过滤器层被 401 拦掉;这里再补一层角色校验,
 * 只放 ADMIN / BOSS 进来(普通用户看仪表盘没意义,数据也不该给)。
 */
@Slf4j
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    /** 统计窗口允许范围 —— 太小看不出趋势,太大拖慢查询 */
    private static final int MIN_DAYS = 7;
    private static final int MAX_DAYS = 90;
    private static final int DEFAULT_DAYS = 30;

    private final StatsService statsService;

    /**
     * 仪表盘统计快照 —— GET /stats/dashboard?days=30
     *
     * @param days 统计窗口天数,夹到 [7, 90],默认 30
     */
    @GetMapping("/dashboard")
    public Result<DashboardStatsResponse> dashboard(
            @RequestParam(name = "days", defaultValue = "" + DEFAULT_DAYS) int days,
            @CurrentUser UserView me) {

        if (me == null) {
            throw new AuthException(ResultCode.UNAUTHORIZED, "未登录");
        }
        if (!UserRole.ADMIN.getCode().equals(me.getRole())
                && !UserRole.BOSS.getCode().equals(me.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可查看统计");
        }

        int safeDays = Math.min(Math.max(days, MIN_DAYS), MAX_DAYS);
        log.info("前端请求/stats/dashboard?days={}", safeDays);
        return Result.success(statsService.dashboard(safeDays));
    }
}
