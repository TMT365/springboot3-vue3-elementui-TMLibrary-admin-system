package com.tmt.TMLibrary.service;


import com.tmt.TMLibrary.common.CaptchaType;
import com.tmt.TMLibrary.dto.request.GetCaptchaRequest;
import com.tmt.TMLibrary.dto.response.CaptchaResponse;


/**
 * 验证码服务 —— 2026-09 重构:
 * <ul>
 *   <li>不再写 {@link jakarta.servlet.http.HttpServletResponse},改成返回 {@link CaptchaResponse}</li>
 *   <li>支持 {@link CaptchaType#LOGIN} / {@link CaptchaType#REGISTER} 两种用途,Redis key 路径不同</li>
 *   <li>返回的 JSON 包含图片(base64 data URI)+ 过期时间戳,前端可做倒计时</li>
 * </ul>
 */
public interface CaptchaService {

    /**
     * 生成 captcha 图片,把元数据(图片文字 + 绑定 username + 过期时间戳)存 Redis,
     * 返回给 controller 包装成 {@code Result<CaptchaResponse>} 返回给前端。
     *
     * @param type    用途(login / register) —— 决定 Redis key 路径
     * @param request 请求体,只取 username 做绑定
     * @param uuid    前端生成,Redis key 后缀
     * @return 含图片 data URI 和过期时间戳的对象
     */
    CaptchaResponse generateCaptcha(CaptchaType type, GetCaptchaRequest request, String uuid);

}
