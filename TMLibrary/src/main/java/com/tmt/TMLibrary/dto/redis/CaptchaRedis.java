package com.tmt.TMLibrary.dto.redis;

import com.tmt.TMLibrary.dto.request.GetCaptchaRequest;
import lombok.Getter;
import lombok.Setter;

/**
 * 存 Redis 的 captcha 元数据 —— 登录和注册共用。
 * <br>字段:
 *   <ul>
 *     <li>captcha:验证码原文(只存这个 + 绑定的 username,不存 password)</li>
 *     <li>username:申请时绑定的用户名,提交时后端会校验一致</li>
 *     <li>expiresAt:毫秒时间戳,前端拿到后做倒计时(2026-09 新增)</li>
 *   </ul>
 */
@Getter
@Setter
public class CaptchaRedis {
    private String captcha;
    private String username;
    /** 毫秒时间戳,前端用来做倒计时显示 */
    private long expiresAt;

    public static CaptchaRedis of(String captchaText, GetCaptchaRequest req, long expiresAtMillis) {
        CaptchaRedis r = new CaptchaRedis();
        r.setCaptcha(captchaText);
        r.setUsername(req.getUsername());
        r.setExpiresAt(expiresAtMillis);
        return r;
    }

    @Override
    public String toString() {
        // 显式覆盖,避免 Lombok 默认 toString 泄露 captcha 原文
        return "CaptchaRedis{captcha=***, username=" + username + ", expiresAt=" + expiresAt + "}";
    }
}
