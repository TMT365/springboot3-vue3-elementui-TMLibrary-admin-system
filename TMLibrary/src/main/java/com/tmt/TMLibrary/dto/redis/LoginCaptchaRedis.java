package com.tmt.TMLibrary.dto.redis;

import com.tmt.TMLibrary.dto.request.GetLoginCaptchaRequestByUsernameAndPassword;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 存 Redis 的登录 captcha 元数据 — 只存 captcha 值 + 绑定的 username。
 * <br>不再存 password：密码不应该出现在 Redis 缓存中（即便短期 3 分钟 TTL）。
 */
@Getter
@Setter
public class LoginCaptchaRedis {
    private String captcha;
    private String username;

    public static LoginCaptchaRedis fromLoginRequest(@NotNull String captcha, @NotNull GetLoginCaptchaRequestByUsernameAndPassword loginRequest) {
        LoginCaptchaRedis loginCaptchaRedis = new LoginCaptchaRedis();
        loginCaptchaRedis.setCaptcha(captcha);
        loginCaptchaRedis.setUsername(loginRequest.getUsername());
        return loginCaptchaRedis;
    }

    @Override
    public String toString() {
        // 显式覆盖避免 Lombok 默认 toString 泄露任何未来字段
        return "LoginCaptchaRedis{captcha=***, username=" + username + "}";
    }
}
