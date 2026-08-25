package com.tmt.TMLibrary.security;

import com.tmt.TMLibrary.common.User.UserRole;
import lombok.Data;

/**
 * @brief 当前登录用户的简化视图(JWT 解析后写到 request attribute)。
 * <p>Controller 临时从 HttpServletRequest.getAttribute("CURRENT_USER") 读,
 * 明天 JwtAuthFilter 接通后由 Filter 自动写入,JwtService.issue 时拼进 claims。
 *
 * <p>不暴露 passwordHash / salt / 任何内部安全字段。
 */
@Data
public class UserView {
    private Integer id;
    private String username;
    private Integer role;
}