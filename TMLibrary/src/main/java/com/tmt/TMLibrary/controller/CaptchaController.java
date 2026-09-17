package com.tmt.TMLibrary.controller;


import com.tmt.TMLibrary.common.CaptchaType;
import com.tmt.TMLibrary.common.Result.Result;
import com.tmt.TMLibrary.dto.request.GetCaptchaRequest;
import com.tmt.TMLibrary.dto.response.CaptchaResponse;
import com.tmt.TMLibrary.service.CaptchaService;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 验证码端点 —— 2026-09 改造:
 * <ul>
 *   <li>响应改成 JSON(原是 image/png 二进制流),便于前端拿 expiresAt 做倒计时</li>
 *   <li>新增 {@code /register} 端点,跟 {@code /login} 共享同一个 service 但 Redis key 走 {@code register:{uuid}} 路径</li>
 * </ul>
 * <p>两个端点都白名单(无需 token),见 JwtAuthFilter</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/captcha")
@RequiredArgsConstructor
public class CaptchaController {

    private final CaptchaService captchaService;

    /**
     * 登录验证码 —— Redis 路径 {@code tmlibrary:captcha:login:{uuid}:code}
     *
     * @param uuid     前端生成的 captcha 唯一标识
     * @param request  请求体,只取 username 做绑定
     */
    @PostMapping("/login")
    public Result<CaptchaResponse> login(
            @RequestParam(name = "uuid", required = true) String uuid,
            @RequestBody(required = true) @Valid GetCaptchaRequest request) {
        log.info("前端请求 /api/captcha/login, uuid={}", uuid);
        return Result.success(captchaService.generateCaptcha(CaptchaType.LOGIN, request, uuid));
    }

    /**
     * 注册验证码 —— Redis 路径 {@code tmlibrary:captcha:register:{uuid}:code}
     * <br>跟 login 路径对称,两个 captcha 元数据互不干扰
     */
    @PostMapping("/register")
    public Result<CaptchaResponse> register(
            @RequestParam(name = "uuid", required = true) String uuid,
            @RequestBody(required = true) @Valid GetCaptchaRequest request) {
        log.info("前端请求 /api/captcha/register, uuid={}", uuid);
        return Result.success(captchaService.generateCaptcha(CaptchaType.REGISTER, request, uuid));
    }
}
