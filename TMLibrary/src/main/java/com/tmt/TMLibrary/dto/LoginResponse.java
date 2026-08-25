package com.tmt.TMLibrary.dto;

import com.tmt.TMLibrary.common.User.UserRole;

/**
 * 用户的登录响应数据传输转换层，将从数据库里面查到的用户id,username,role等信息返回给前端。加上token，前端可以在后续的请求中携带token来进行身份验证。
 * LoginResponse
 */
public class LoginResponse {
    private String token;
    private String username;
    private UserRole role;

    public LoginResponse(String token, String username, UserRole role) {
        this.token = token;
        this.username = username;
        this.role = role;
    }

    // Getters and setters
    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }
}
