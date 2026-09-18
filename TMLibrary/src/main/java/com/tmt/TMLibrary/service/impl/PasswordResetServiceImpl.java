package com.tmt.TMLibrary.service.impl;

import com.tmt.TMLibrary.common.Result.ResultCode;
import com.tmt.TMLibrary.common.redis.RedisKeys;
import com.tmt.TMLibrary.entity.User;
import com.tmt.TMLibrary.exception.BusinessException;
import com.tmt.TMLibrary.mapper.UserMapper;
import com.tmt.TMLibrary.service.MailService;
import com.tmt.TMLibrary.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * 密码重置实现。
 *
 * <h2>令牌怎么生成、怎么存</h2>
 * <ul>
 *   <li><b>生成</b>:{@link SecureRandom} 取 32 字节 → Base64URL 无填充 → 43 字符。
 *       用 SecureRandom 而不是 {@code Random}/{@code UUID.randomUUID()} ——
 *       后两者的输出可预测,而这个字符串等价于一次登录凭据。</li>
 *   <li><b>存</b>:库里存的是令牌的 <b>SHA-256 十六进制</b>(64 字符,正好填满
 *       {@code VARCHAR(64)} 列),不是明文。这样库被拖走也拿不到能直接用的令牌 ——
 *       和"密码存 bcrypt 不存明文"是同一个道理。</li>
 *   <li><b>为什么用 SHA-256 而不是 bcrypt</b>:令牌是 32 字节真随机,
 *       不像人选的密码那样有低熵问题,不需要慢哈希来抗爆破;
 *       而且这个查询在公开端点上,用 bcrypt 会让每次校验固定消耗
 *       ~100ms CPU —— 那本身就是个 DoS 放大点。</li>
 * </ul>
 *
 * <h2>一次性怎么保证</h2>
 * <p>重置成功后立即 {@code clearResetToken} 把两个字段置 NULL。
 * 查询 SQL 里也带了 {@code expiration > NOW()},过期令牌查不出来。</p>
 *
 * <h2>已知未做</h2>
 * <p>重置密码后<b>不会</b>让该用户已签发的 JWT 失效 —— 那些 token 在
 * 有效期(默认 7 天)内仍然可用。要做到这点需要按用户维度追踪 jti
 * (现有黑名单只按单个 jti 记),属于另一个量级的工作。攻击者若已拿到
 * 有效 token,重置密码并不能把他踢下线。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    /** 令牌有效期。够走完"打开邮箱 → 点链接",又不至于长期有效 */
    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    /** 32 字节随机 → Base64URL 无填充 = 43 字符 */
    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserMapper userMapper;
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate stringRedisTemplate;

    /** 前端地址,用来拼重置链接。必须是**浏览器能访问到的**地址,不是后端地址 */
    @Value("${app.frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    // ============================================================
    // 申请重置
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void requestReset(String email) {
        List<User> matched = userMapper.selectUsersByEmail(email);

        // ---- 情况 1:没有这个邮箱 ----
        // 直接返回。**不能**抛异常、也不能返回不同的话术,
        // 否则这个接口就成了"批量探测哪些邮箱注册过"的工具。
        if (matched.isEmpty()) {
            log.info("密码重置申请:邮箱未注册,静默忽略");
            return;
        }

        // ---- 情况 2:一个邮箱命中多个账号 ----
        // users.email 没有唯一约束,理论上可能。这时**拒绝发信** ——
        // 发给这个共用邮箱等于把其中一个账号的重置权交给另一个账号的主人。
        // 对外同样是静默成功,只在日志里告警(这是数据问题,得人工清理)。
        if (matched.size() > 1) {
            log.warn("密码重置申请:邮箱命中 {} 个账号,拒绝发信(需要先清理重复邮箱)", matched.size());
            return;
        }

        User user = matched.get(0);

        // ---- 情况 3:正常路径 ----
        String rawToken = generateToken();
        userMapper.setResetToken(user.getId(), sha256Hex(rawToken), LocalDateTime.now().plus(TOKEN_TTL));

        String resetUrl = frontendBaseUrl.replaceAll("/+$", "") + "/reset-password?token=" + rawToken;
        // 发送失败在 MailService 内部就消化掉了,不会走到这里抛出来
        mailService.sendPasswordReset(user.getEmail(), user.getUsername(), resetUrl);

        log.info("密码重置申请已受理: userId={}", user.getId());
    }

    // ============================================================
    // 执行重置
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(String token, String newPassword) {
        // 与注册/改密保持一致的长度约束(这里再挡一道,DTO 校验被绕过时兜底)
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 20) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "密码长度不能小于6位且不能大于20位");
        }

        // 过期判断在 SQL 里(WHERE ... > NOW()),查不到就是无效或过期,不区分 ——
        // 区分了等于告诉攻击者"这个令牌曾经存在过"
        User user = userMapper.selectUserByResetToken(sha256Hex(token));
        if (user == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "重置链接无效或已过期,请重新申请");
        }

        // 新密码和旧密码相同就直接拒绝。改了个寂寞,而且用户会以为"改成功了"，
        // 下次还是用旧密码登录 —— 不如明确告知
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "新密码不能与原密码相同");
        }

        User update = new User();
        update.setId(user.getId());
        update.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateUserById(user.getId(), update);

        // ⚠️ 必须清 by-username 缓存,否则**用户拿新密码登不进去**。
        //
        // AuthServiceImpl 登录时先读 Redis 里的 UserRedis(含 passwordHash),
        // 命中就不再查库 —— 只写了库不清缓存,登录校验拿到的还是旧哈希,
        // 于是新密码被判为"密码错误",而旧密码在缓存过期(30 分钟)前依然能登。
        // 这不是理论风险:实测踩到过,新密码 401 / 旧密码 200。
        //
        // 和 UserManagementServiceImpl 里改密/改资料/软删的处理保持一致。
        evictUserCache(user.getUsername());

        // 一次性:立刻作废令牌,防止同一链接被重复使用
        userMapper.clearResetToken(user.getId());

        log.info("密码已重置: userId={}", user.getId());
    }

    /**
     * 删掉该用户的 by-username 缓存。
     *
     * <p>缓存只是加速层,删失败不该让重置失败 —— 最坏情况是用户
     * 30 分钟内登不进去,所以这里吞掉异常只记日志
     * (与 {@code AuthServiceImpl.refreshUserCache} 的处置方式一致)。</p>
     */
    private void evictUserCache(String username) {
        try {
            stringRedisTemplate.delete(RedisKeys.userByUsername(username));
        } catch (Exception e) {
            log.warn("清除用户缓存失败(用户在缓存过期前可能仍无法用新密码登录): username={}, err={}",
                    username, e.getMessage());
        }
    }

    // ============================================================
    // 工具
    // ============================================================

    private static String generateToken() {
        byte[] buf = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(buf);
        // Base64URL 无填充:不带 + / = 这些在 URL 和邮件正文里需要转义的字符
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    /** 令牌哈希。十六进制小写,64 字符,与 password_reset_token VARCHAR(64) 对齐 */
    private static String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 强制要求支持的算法,走不到这里
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
