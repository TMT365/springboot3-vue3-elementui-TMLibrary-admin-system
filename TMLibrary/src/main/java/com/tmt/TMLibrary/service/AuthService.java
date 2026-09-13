package com.tmt.TMLibrary.service;

import com.tmt.TMLibrary.dto.response.LoginResponse;
import com.tmt.TMLibrary.dto.response.LogoutResponse;
import com.tmt.TMLibrary.dto.request.LoginRequest;
import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {
    /**
     * 登录方法，接收登录请求对象，返回登录响应对象
     * @param loginRequest 登录请求对象
     * @return 登录响应对象
     */
    public abstract LoginResponse login(LoginRequest loginRequest);

    /**
     * 登出方法:将 token 的 jti 写入 Redis 黑名单(剩余 TTL),后续请求会被 JwtAuthFilter 拦截。
     * 返回 {@link LogoutResponse} 包含明确的重定向信号,前端据此跳转回欢迎/登录页。
     *
     * @param request HTTP 请求(从中解析 Authorization 头)
     * @return 登出结果(给前端一个明确的跳转信号)
     */
    public abstract LogoutResponse logout(HttpServletRequest request);
}
