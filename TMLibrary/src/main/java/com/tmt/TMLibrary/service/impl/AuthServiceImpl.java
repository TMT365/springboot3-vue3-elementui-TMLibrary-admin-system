package com.tmt.TMLibrary.service.impl;

import com.tmt.TMLibrary.common.User.UserRole;
import com.tmt.TMLibrary.common.redis.RedisKeys;
import com.tmt.TMLibrary.common.utils.RandomExpirationTimeWithOffset;
import com.tmt.TMLibrary.dto.redis.LoginCaptchaRedis;
import com.tmt.TMLibrary.exception.BusinessException;
import com.tmt.TMLibrary.service.AuthService;
import com.tmt.TMLibrary.dto.response.LoginResponse;
import com.tmt.TMLibrary.dto.response.LogoutResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.tmt.TMLibrary.dto.request.LoginRequest;
import com.tmt.TMLibrary.security.jwt.JwtService;
import com.tmt.TMLibrary.entity.User;
import com.tmt.TMLibrary.exception.AuthException;
import com.tmt.TMLibrary.mapper.UserMapper;
import org.springframework.stereotype.Service;
import com.tmt.TMLibrary.common.Result.ResultCode;
import com.tmt.TMLibrary.common.User.UserStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import io.jsonwebtoken.Claims;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.tmt.TMLibrary.dto.redis.UserRedis;


/**
 * 用户登录成功后，不仅是返回一个token和需要返回用户的角色信息，而且还需要对{@link com.tmt.TMLibrary.entity.User}的其它不准用户请求的敏感信息进行过滤，避免返回给前端。这里我们使用{@link LoginResponse}来封装返回给前端的用户信息。
 * 
 * {@link AuthServiceImpl}
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    // Redis key 由 RedisKeys 统一管理,本类不再硬编码
    // 旧常量(已删除):
    //   REDIS_JWT_BLACK_PATH      = "tmlibrary:user:jwt:blackList:"      ← 错配,Filter 读 auth:
    //   REDIS_CAPTCHA_LOGIN_PATH  = "tmlibrary:captcha:login:"
    //   REDIS_USERS_INFO_BY_USERNAME_PATH = "tmlibrary:user:users:username:"

    // Autowired constructor injection for JwtService and UserMapper
    public AuthServiceImpl(PasswordEncoder passwordEncoder, JwtService jwtService, UserMapper userMapper, StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // 登录失败锁定策略 — 集中常量便于调整
    private static final int FAILED_LOGIN_THRESHOLD  = 3;    // 累计失败 N 次锁定
    private static final int LOCK_DURATION_MINUTES  = 15;   // 锁定时长

    /**
     * 负缓存哨兵 — 用户不存在时写入该 JSON,而不是空串。
     * <p>空串有两个坑:① {@code trim().isEmpty()} 判定与 {@code readValue("")} 边界混淆;
     * ② 反序列化空串会抛 JsonProcessingException。用显式哨兵值可读且安全。</p>
     */
    private static final String NEGATIVE_SENTINEL = "{\"__negative__\":true}";

    @Override
    public LoginResponse login(LoginRequest loginRequest) {
        String uuid = loginRequest.getUuid();
        // 先验证验证码
        String jsonString = stringRedisTemplate.opsForValue().get(RedisKeys.captchaLogin(uuid.trim()));
        if (jsonString == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "未找到请求验证码");
        }
        LoginCaptchaRedis loginCaptchaRedis;
        try {
            // 2. 只有解析这一步才会出现意料之外的异常：脏json、格式错乱
            loginCaptchaRedis = objectMapper.readValue(jsonString, LoginCaptchaRedis.class);
        } catch (JacksonException e) {
            log.error("验证码Redis数据解析异常，uuid:{} , json:{}", uuid, jsonString, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "验证码数据异常，请重新获取验证码");
        }

        // 后续：验证码内容 + 绑定用户名校验
        // 注意：captcha 不再绑定 password —— 密码验证交给后文 BCrypt
        if(!loginCaptchaRedis.getUsername().equals(loginRequest.getUsername())){
            throw new BusinessException(ResultCode.BAD_REQUEST,"验证码与账号不匹配");
        }
        if(!loginCaptchaRedis.getCaptcha().equals(loginRequest.getCaptcha())){
            throw new BusinessException(ResultCode.BAD_REQUEST,"验证码错误");
        }

        // 校验通过，删除验证码，防止重复使用
        stringRedisTemplate.delete(RedisKeys.captchaLogin(uuid.trim()));


        // 从Redis里面拿数据，未命中去数据库
        jsonString = stringRedisTemplate.opsForValue().get(RedisKeys.userByUsername(loginRequest.getUsername()));
        User user = null;
        if (jsonString != null && !jsonString.trim().isEmpty()
                && !NEGATIVE_SENTINEL.equals(jsonString)) {
            // 命中正缓存 → 反序列化
            user = User.fromUserRedis(objectMapper.readValue(jsonString, UserRedis.class));
        } else if (NEGATIVE_SENTINEL.equals(jsonString)) {
            // 命中负缓存 — 该用户名在缓存 TTL 内确认不存在,直接拒绝(不再打 DB,防穿透)
            throw new AuthException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        } else {
            // 缓存未命中 → 查 DB,并回填(存在写正缓存 / 不存在写负 sentinel)
            user = userMapper.selectUserByUsername(loginRequest.getUsername());
            if (user != null) {
                UserRedis userRedis = UserRedis.fromUser(user);
                String json = objectMapper.writeValueAsString(userRedis);
                stringRedisTemplate.opsForValue().set(
                    RedisKeys.userByUsername(loginRequest.getUsername()),
                    json, Expiration.from(30L, TimeUnit.MINUTES));
            } else {
                stringRedisTemplate.opsForValue().set(
                    RedisKeys.userByUsername(loginRequest.getUsername()),
                    NEGATIVE_SENTINEL,
                    RandomExpirationTimeWithOffset.get(3L, TimeUnit.MINUTES));
            }
        }

        // 反枚举:user 不存在 / 用户名错 / 密码错 全部用同一文案,防止账号枚举
        if (user == null || !user.getUsername().equals(loginRequest.getUsername())) {
            throw new AuthException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        // 判断用户状态是否为ACTIVE 或是 账户是否被锁定
        if (!user.getStatus().equals(UserStatus.ACTIVE.getCode())) {
            throw new AuthException(ResultCode.FORBIDDEN, "用户账户未激活");
        }

        // 判断是否被软删除
        if (user.getDeletedAt() != null) {
            throw new AuthException(ResultCode.FORBIDDEN, "用户账户已被删除");
        }

        LocalDateTime accountLockedUntilById = userMapper.getAccountLockedUntilById(user.getId());
        if (accountLockedUntilById != null && accountLockedUntilById.isAfter(LocalDateTime.now())) {
            // 仍在锁定中
            throw new AuthException(ResultCode.FORBIDDEN, "用户账户已被锁定，请稍后再试，解除锁时间：" + accountLockedUntilById.format(TIME_FORMATTER));
        }
        if (accountLockedUntilById != null) {
            // 锁定已过期 → 重置失败计数,允许重新累计
            userMapper.resetFailedLoginAttemptsById(user.getId());
        }

        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPasswordHash())) {
            // 原子递增失败次数 + 阈值触发时一并锁定(单条 SQL,无读改写竞态)
            userMapper.incrementAndMaybeLock(user.getId(), FAILED_LOGIN_THRESHOLD, LOCK_DURATION_MINUTES);
            // 查最新 lock 状态,判断是否刚被锁定
            LocalDateTime newLockUntil = userMapper.getAccountLockedUntilById(user.getId());
            if (newLockUntil != null && newLockUntil.isAfter(LocalDateTime.now())) {
                throw new AuthException(ResultCode.FORBIDDEN,
                    "用户账户已被锁定，请稍后再试，解除锁时间：" + newLockUntil.format(TIME_FORMATTER));
            }
            // 反枚举:用户不存在 / 密码错 同消息(已在前面统一)
            throw new AuthException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        // 更新 lastLoginTime / lastLoginIp
        LocalDateTime loginAt = LocalDateTime.now();
        userMapper.setLastLoginTimeById(user.getId(), loginAt);
        userMapper.setLastLoginIpById(user.getId(), loginRequest.getIpAddress());

        // R7: DB 更新完成后再刷新缓存 — 保证缓存快照与已提交的 DB 状态一致
        // (旧代码在密码校验前就写缓存,顺序错误;且 UserRedis 未含登录时间字段,
        //  这里显式重写一遍,后续若 UserRedis 扩容字段也不会读到登录前快照)
        refreshUserCache(user);

        String jti = UUID.randomUUID().toString().replace("-", "");
        // 生成JWT token
        String token = jwtService.issue(user, jti);
        // 登出时，将jti作为key，剩余时间TTL，放入blackList:jti中，时间一过自动清除
        return new LoginResponse(token, user.getUsername(), UserRole.getUserRoleByCode(user.getRole()));
    }

    @Override
    public LogoutResponse logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new AuthException(ResultCode.UNAUTHORIZED, "缺少 Authorization 头");
        }
        // 拿出 Authorization Bearer token
        String token = authHeader.substring(7).trim();

        // 解析 token,获得剩余时间和 jti
        Claims claims = jwtService.parse(token);
        String jti = claims.getId();
        if (jti == null || jti.isEmpty()) {
            throw new AuthException(ResultCode.NOT_FOUND, "不是最新的JWT令牌,请重新登录");
        }

        // 写入黑名单:TTL 取 token 剩余时间,过期自动清理
        Long remainTime = getRemainTime(claims.getExpiration());
        if (remainTime != null && remainTime > 0) {
            stringRedisTemplate.opsForValue().set(RedisKeys.jwtBlacklist(jti), token, Expiration.milliseconds(remainTime));
            log.info("JWT added to blacklist, jti={}, ttlMs={}", jti, remainTime);
        }

        // 给前端一个明确的重定向信号 — 前端拦截器见 loggedOut=true 应清 localStorage 并跳转 /login
        return new LogoutResponse(true, "登出成功,请重新登录", "/login", Instant.now());
    }

    /**
     * 登录成功后刷新用户正缓存 — 用当前 DB 快照覆盖,避免读到登录前状态。
     * <p>写失败不影响登录主流程(缓存只是加速,DB 才是权威)。</p>
     */
    private void refreshUserCache(User user) {
        try {
            UserRedis fresh = UserRedis.fromUser(user);
            stringRedisTemplate.opsForValue().set(
                RedisKeys.userByUsername(user.getUsername()),
                objectMapper.writeValueAsString(fresh),
                Expiration.from(30L, TimeUnit.MINUTES));
        } catch (Exception e) {
            log.warn("刷新用户缓存失败 username={}, err={}", user.getUsername(), e.getMessage());
        }
    }

    private Long getRemainTime(Date expiration){
        // 获取现在时间 ms
        // 吧过期时间变成ms级时间戳
        return expiration.getTime() - System.currentTimeMillis();
    }
}
