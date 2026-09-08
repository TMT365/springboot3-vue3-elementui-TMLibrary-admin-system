package com.tmt.TMLibrary.service.impl;

import com.tmt.TMLibrary.common.User.UserRole;
import com.tmt.TMLibrary.common.utils.RandomExpirationTimeWithOffset;
import com.tmt.TMLibrary.dto.redis.LoginCaptchaRedis;
import com.tmt.TMLibrary.exception.BusinessException;
import com.tmt.TMLibrary.service.AuthService;
import com.tmt.TMLibrary.dto.response.LoginResponse;
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

    private static final String REDIS_JWT_BLACK_PATH = "tmlibrary:user:jwt:blackList:";
    private static final String REDIS_CAPTCHA_LOGIN_PATH = "tmlibrary:captcha:login:";
    private static final String REDIS_USERS_INFO_BY_USERNAME_PATH = "tmlibrary:user:users:username:";

    // Autowired constructor injection for JwtService and UserMapper
    public AuthServiceImpl(PasswordEncoder passwordEncoder, JwtService jwtService, UserMapper userMapper, StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public LoginResponse login(LoginRequest loginRequest) {
        String uuid = loginRequest.getUuid();
        // 先验证验证码
        String jsonString = stringRedisTemplate.opsForValue().get(REDIS_CAPTCHA_LOGIN_PATH + uuid.trim());
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
        stringRedisTemplate.delete(REDIS_CAPTCHA_LOGIN_PATH + uuid.trim());


        // 从Redis里面拿数据，未命中去数据库
        jsonString = stringRedisTemplate.opsForValue().get(REDIS_USERS_INFO_BY_USERNAME_PATH + loginRequest.getUsername());
        User user;
        if (jsonString == null || jsonString.trim().isEmpty()) {
            // 如果 Redis 里面不存在，就去数据库里面查看
            user = userMapper.selectUserByUsername(loginRequest.getUsername());
            // 如果 user 存在就写入 Redis
            if (user != null){
                UserRedis userRedis = UserRedis.fromUser(user);
                String json = objectMapper.writeValueAsString(userRedis);
                stringRedisTemplate.opsForValue().set(REDIS_USERS_INFO_BY_USERNAME_PATH + loginRequest.getUsername(), json, Expiration.from(30L, TimeUnit.MINUTES));
            }
            // 如果不存在写入空值
            stringRedisTemplate.opsForValue().set(REDIS_USERS_INFO_BY_USERNAME_PATH + loginRequest.getUsername(), "", RandomExpirationTimeWithOffset.get(3L, TimeUnit.MINUTES));
        } else {
            // jsonString存在就反序列化为对象
            user = User.fromUserRedis(objectMapper.readValue(jsonString, UserRedis.class));
        }

        if (user == null) {
            throw new AuthException(ResultCode.UNAUTHORIZED, "用户不存在");
        }
        // 判断用户名
        if (!user.getUsername().equals(loginRequest.getUsername())) {
            throw new AuthException(ResultCode.BAD_REQUEST, "用户名错误");
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
        if ((accountLockedUntilById != null &&accountLockedUntilById.isAfter(LocalDateTime.now()))){
            throw new AuthException(ResultCode.FORBIDDEN, "用户账户已被锁定，请稍后再试，解除锁时间：" + accountLockedUntilById.format(TIME_FORMATTER));
        }

        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPasswordHash())){
            // 密码错误，增加连续登录失败次数
            userMapper.incrementFailedLoginAttemptsById(user.getId());
            // 如果连续登录失败次数超过阈值，锁定账户
            if (userMapper.AcquiredFailedLoginAttempts(user.getId()) + 1 >= 3) { // �假设阈值为3次
                LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(15); // 锁定15分钟
                userMapper.lockUserAccountById(user.getId(), lockUntil);
                throw new AuthException(ResultCode.FORBIDDEN, "用户账户已被锁定，请稍后再试，解除锁时间：" + lockUntil.format(TIME_FORMATTER));
            }
            throw new AuthException(ResultCode.UNAUTHORIZED, "密码错误");
        }
        // 登录成功，重置连续登录失败次数
        userMapper.resetFailedLoginAttemptsById(user.getId());
        // 更新用户的最后登录时间、IP地址
        // 更新 lastLoginTime 和 lastLoginIp
        userMapper.setLastLoginTimeById(user.getId(), LocalDateTime.now());
        userMapper.setLastLoginIpById(user.getId(), loginRequest.getIpAddress());

        String jti = UUID.randomUUID().toString().replace("-", "");
        // 生成JWT token
        String token = jwtService.issue(user, jti);
        // 登出时，将jti作为key，剩余时间TTL，放入blackList:jti中，时间一过自动清除
        return new LoginResponse(token, user.getUsername(), UserRole.getUserRoleByCode(user.getRole()));
    }

    @Override
    public void logout(HttpServletRequest request){
        // 拿出 Authorization Bearer token
        String token = request.getHeader("Authorization").substring("Bearer ".length());
        // getHeader 和 getAttribute 区分

        // 解析token，获得剩余时间和 jti
        Claims claims = jwtService.parse(token);
        // 提取
        String jti =  claims.getId();
        // 判断是否是空
        if (jti.isEmpty()){
            throw new AuthException(ResultCode.NOT_FOUND, "不是最新的JWT令牌，请重新登录");
        }

        Long remainTime = getRemainTime(claims.getExpiration());
        // 使用 Redis
        stringRedisTemplate.opsForValue().set(REDIS_JWT_BLACK_PATH + jti, token, Expiration.milliseconds(remainTime));
    }

    private Long getRemainTime(Date expiration){
        // 获取现在时间 ms
        // 吧过期时间变成ms级时间戳
        return expiration.getTime() - System.currentTimeMillis();
    }
}
