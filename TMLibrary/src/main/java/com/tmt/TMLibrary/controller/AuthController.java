package com.tmt.TMLibrary.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;

import com.tmt.TMLibrary.service.AuthService;
import com.tmt.TMLibrary.dto.LoginRequest;
import com.tmt.TMLibrary.dto.LoginResponse;
import com.tmt.TMLibrary.common.Result.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import com.tmt.TMLibrary.common.utils.IpUtil;

@Slf4j
@Controller
@ResponseBody
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginResponse> login(HttpServletRequest request, @RequestBody @Valid LoginRequest loginRequest) {
        loginRequest.setIpAddress(IpUtil.getClientIp(request));
        log.info("前端请求/api/auth/login, 参数={}", loginRequest);
        LoginResponse response = authService.login(loginRequest);
        // authService.login()方法中已经处理了用户登录失败的情况，并抛出了相应的异常，这里不需要再处理登录失败的情况，只需要返回登录成功的结果即可。
        // 异常会被全局异常处理器捕获并返回给前端。
        return Result.success(response);
    }

}
