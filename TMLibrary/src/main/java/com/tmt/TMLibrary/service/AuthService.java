package com.tmt.TMLibrary.service;

import com.tmt.TMLibrary.dto.LoginResponse;
import com.tmt.TMLibrary.dto.LoginRequest;

public interface AuthService {
    /**
     * 登录方法，接收登录请求对象，返回登录响应对象
     * @param loginRequest 登录请求对象
     * @return 登录响应对象
     */
    public abstract LoginResponse login(LoginRequest loginRequest);
}
