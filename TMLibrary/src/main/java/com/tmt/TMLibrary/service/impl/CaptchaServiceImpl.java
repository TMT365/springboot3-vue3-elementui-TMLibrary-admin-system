package com.tmt.TMLibrary.service.impl;

import com.tmt.TMLibrary.common.utils.CaptchaUtil;
import com.tmt.TMLibrary.dto.redis.LoginCaptchaRedis;
import com.tmt.TMLibrary.dto.request.GetLoginCaptchaRequestByUsernameAndPassword;
import com.tmt.TMLibrary.exception.BusinessException;
import com.tmt.TMLibrary.service.CaptchaService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.util.concurrent.TimeUnit;
import com.tmt.TMLibrary.common.Result.ResultCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class CaptchaServiceImpl implements CaptchaService {

    private static final String REDIS_CAPTCHA_LOGIN_PATH = "tmlibrary:captcha:login:";
    private static final String REDIS_CAPTCHA_REGISTER_PATH = "tmlibrary:captcha:register:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 请求登录验证码，设置 3 分钟的过期时间，同时保存请求时的 username，
     * 做到"验证码和获取时的用户"一致性 — 防止 A 拿到的 captcha 被 B 拿去试密码。
     *
     * @param response     HttpServletResponse 写入验证码图片
     * @param loginRequest 登录请求（仅含 username）
     * @param uuid         前端传参的 uuid，验证码唯一标识符
     */
    @Override
    public void getLoginCaptchaService(HttpServletResponse response, GetLoginCaptchaRequestByUsernameAndPassword loginRequest, String uuid) {
        String captcha = CaptchaUtil.CreateCaptchaImage(response);
        LoginCaptchaRedis loginCaptchaRedis = LoginCaptchaRedis.fromLoginRequest(captcha, loginRequest);
        String json = objectMapper.writeValueAsString(loginCaptchaRedis);
        // 将 captcha 元数据放入 Redis — 不再含 password
        String redisKey = REDIS_CAPTCHA_LOGIN_PATH + uuid.trim();
        stringRedisTemplate.opsForValue().set(redisKey, json, Expiration.from(3L, TimeUnit.MINUTES));
    }


}
