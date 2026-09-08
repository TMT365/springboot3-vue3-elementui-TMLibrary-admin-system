package com.tmt.TMLibrary.controller;


import com.tmt.TMLibrary.dto.request.GetLoginCaptchaRequestByUsernameAndPassword;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.tmt.TMLibrary.service.CaptchaService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/captcha")
@RequiredArgsConstructor
public class CaptchaController {

    private final CaptchaService captchaService;

    /**
     * 获取登录验证码 — POST /api/captcha/login?uuid=xxx
     * <br>请求体只含 username；响应体是验证码图片（image/png）。
     *
     * @param response    写入验证码图片
     * @param uuid        前端生成的 captcha 唯一标识
     * @param loginRequest 仅含 username
     */
    @PostMapping("/login")
    public void captcha(HttpServletResponse response,
                        @RequestParam(name = "uuid", required = true) String uuid,
                        @RequestBody(required = true) @Valid GetLoginCaptchaRequestByUsernameAndPassword loginRequest) {

        captchaService.getLoginCaptchaService(response, loginRequest, uuid);
    }
}
