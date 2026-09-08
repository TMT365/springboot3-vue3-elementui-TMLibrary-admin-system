package com.tmt.TMLibrary.dto.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 获取登录验证码的请求 — 仅传 username（用于绑定 captcha 与用户），不传密码。
 * <br>captcha 的目的是"密码提交前的校验"，请求体里不能再带密码（鸡生蛋）。
 */
@Getter
@Setter
public class GetLoginCaptchaRequestByUsernameAndPassword {
    private String username;

    @Override
    public String toString() {
        // 显式覆盖 Lombok 默认 toString，确保未来加敏感字段也不会泄露
        return "GetLoginCaptchaRequest{username=" + username + "}";
    }
}
