package com.tmt.TMLibrary.dto.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 获取验证码的请求体 —— 登录和注册共用。
 * <br>captcha 的目的是"密码提交前的校验",请求体里不能再带密码(鸡生蛋)。
 * <br>username 用于把 captcha 跟用户名绑定 —— 提交时后端校验一致。
 */
@Getter
@Setter
public class GetCaptchaRequest {
    private String username;

    @Override
    public String toString() {
        // 显式覆盖 Lombok 默认 toString,防止未来加敏感字段意外泄露
        return "GetCaptchaRequest{username=" + username + "}";
    }
}
