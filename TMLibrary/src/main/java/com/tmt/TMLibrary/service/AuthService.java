package com.tmt.TMLibrary.service;

import com.tmt.TMLibrary.dto.response.LoginResponse;
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
     * 登出方法，将用户 JWT token Payload 里面的 id(jti) 取出来，放入 jwt:blackList:jti 中
     * @param request HTTP 请求(从中解析 Authorization 头)
     */
    public abstract void logout(HttpServletRequest request);
}
