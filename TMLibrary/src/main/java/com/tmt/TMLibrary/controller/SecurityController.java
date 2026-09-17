package com.tmt.TMLibrary.controller;

import com.tmt.TMLibrary.common.Result.Result;
import com.tmt.TMLibrary.common.Result.ResultCode;
import com.tmt.TMLibrary.common.User.UserRole;
import com.tmt.TMLibrary.entity.IpBan;
import com.tmt.TMLibrary.exception.AuthException;
import com.tmt.TMLibrary.exception.BusinessException;
import com.tmt.TMLibrary.security.context.CurrentUser;
import com.tmt.TMLibrary.security.context.UserView;
import com.tmt.TMLibrary.service.IpBanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 风控运维接口 —— 给管理员查看/解除 IP 封禁。
 *
 * <p>没有这两个端点的话,误封只能手写 SQL + 手动清 Redis,很容易出错
 * (只清 DB 不清 Redis 的话,封禁会一直生效到 TTL 结束)。</p>
 *
 * <p>鉴权:{@code /api/security/**} 不在白名单 → 过滤器层要求 token;
 * 这里再校验仅 ADMIN / BOSS。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/security")
@RequiredArgsConstructor
public class SecurityController {

    private final IpBanService ipBanService;

    /** 生效中的封禁列表 —— GET /api/security/ip-bans */
    @GetMapping("/ip-bans")
    public Result<List<IpBan>> listBans(@CurrentUser UserView me) {
        requireAdmin(me);
        return Result.success(ipBanService.listActiveBans());
    }

    /** 人工解封 —— DELETE /api/security/ip-bans/{ip}(同时清 DB 记录与 Redis 标记) */
    @DeleteMapping("/ip-bans/{ip}")
    public Result<Void> unban(@PathVariable("ip") String ip, @CurrentUser UserView me) {
        requireAdmin(me);
        if (ip == null || ip.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "IP 不能为空");
        }
        log.info("管理员 {} 解封 IP: {}", me.getUsername(), ip);
        ipBanService.unban(ip);
        return Result.success();
    }

    private void requireAdmin(UserView me) {
        if (me == null) {
            throw new AuthException(ResultCode.UNAUTHORIZED, "未登录");
        }
        if (!UserRole.ADMIN.getCode().equals(me.getRole())
                && !UserRole.BOSS.getCode().equals(me.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可操作风控");
        }
    }
}
