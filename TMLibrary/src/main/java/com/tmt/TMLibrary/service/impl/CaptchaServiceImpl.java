package com.tmt.TMLibrary.service.impl;

import com.tmt.TMLibrary.common.CaptchaType;
import com.tmt.TMLibrary.common.redis.RedisKeys;
import com.tmt.TMLibrary.common.utils.CaptchaUtil;
import com.tmt.TMLibrary.dto.redis.CaptchaRedis;
import com.tmt.TMLibrary.dto.request.GetCaptchaRequest;
import com.tmt.TMLibrary.dto.response.CaptchaResponse;
import com.tmt.TMLibrary.exception.BusinessException;
import com.tmt.TMLibrary.service.CaptchaService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.concurrent.TimeUnit;
import com.tmt.TMLibrary.common.Result.ResultCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class CaptchaServiceImpl implements CaptchaService {

    /** captcha 在 Redis 里的 TTL —— 跟前端倒计时一致,都是 3 分钟 */
    public static final long CAPTCHA_TTL_MINUTES = 3;
    public static final long CAPTCHA_TTL_MS = CAPTCHA_TTL_MINUTES * 60 * 1000;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 生成 captcha 图片 + 存元数据到 Redis,返回 {@link CaptchaResponse}。
     * <p>流程:
     * <ol>
     *   <li>{@link CaptchaUtil#generateCaptchaImage()} 生成文字 + JPEG 字节流</li>
     *   <li>算 expiresAt = now + 3 分钟(给前端倒计时)</li>
     *   <li>把"文字 + username + expiresAt"序列化成 JSON 写 Redis,TTL 3 分钟</li>
     *   <li>把 JPEG 编码成 base64 data URI,跟 expiresAt 一起返回</li>
     * </ol>
     * <p>同一个 uuid 只能验证一次(登录/注册成功后由 AuthService/UserService 删除 Redis key)。
     */
    @Override
    public CaptchaResponse generateCaptcha(CaptchaType type, GetCaptchaRequest request, String uuid) {
        // 1. 校验 type
        if (type == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "captcha 类型不能为空");
        }

        // 2. 生成图片 + 文字
        CaptchaUtil.CaptchaImage ci = CaptchaUtil.generateCaptchaImage();
        String captchaText = ci.text();
        byte[] jpegBytes = ci.jpegBytes();

        // 3. 算过期时间戳
        long now = System.currentTimeMillis();
        long expiresAt = now + CAPTCHA_TTL_MS;

        // 4. 写 Redis,按 type 选路径(login:{uuid} 或 register:{uuid})
        //    key 里不带 username(2026-09 去掉)—— username 仍存进 value,
        //    提交时由 AuthServiceImpl / UserManagementServiceImpl 做一致性校验。
        //    见 RedisKeys.CAPTCHA_LOGIN 的「为什么 key 里不再带 username」。
        String key = (type == CaptchaType.LOGIN)
            ? RedisKeys.captchaLogin(uuid.trim())
            : RedisKeys.captchaRegister(uuid.trim());

        CaptchaRedis meta = CaptchaRedis.of(captchaText, request, expiresAt);
        try {
            String json = objectMapper.writeValueAsString(meta);
            stringRedisTemplate.opsForValue().set(key, json,
                Expiration.from(CAPTCHA_TTL_MS, TimeUnit.MILLISECONDS));
        } catch (JacksonException e) {
            log.error("captcha 元数据序列化失败: uuid={}, type={}", uuid, type, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "验证码生成失败", e);
        }

        // 5. 拼 data URI 返回 —— 不要再写 HttpServletResponse
        String dataUri = "data:image/jpeg;base64,"
            + Base64.getEncoder().encodeToString(jpegBytes);

        log.info("生成 {} 验证码: uuid={}, username={}, expiresAt={}",
            type, uuid, request.getUsername(), expiresAt);
        // expiresAt 序列化成 String —— 见 CaptchaResponse 类注释「string at the boundary」
        return new CaptchaResponse(dataUri, String.valueOf(expiresAt));
    }
}
