package com.tmt.TMLibrary.service;


import com.tmt.TMLibrary.dto.request.GetLoginCaptchaRequestByUsernameAndPassword;
import jakarta.servlet.http.HttpServletResponse;


public interface CaptchaService {

    /**
     * 生成登录验证码，写入 HTTP response，并将 captcha 绑定到 username 存入 Redis。
     * <br>不再接收 password —— captcha 是密码提交前的防线，请求体不该出现密码。
     *
     * @param response     HTTP 响应（写入验证码图片）
     * @param loginRequest 请求体（仅含 username）
     * @param uuid         前端生成的 captcha 唯一标识
     */
    void getLoginCaptchaService(HttpServletResponse response, GetLoginCaptchaRequestByUsernameAndPassword loginRequest, String uuid);

}
