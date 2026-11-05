package com.tmt.TMLibrary.service.impl;

import com.tmt.TMLibrary.common.User.UserRole;
import com.tmt.TMLibrary.common.User.UserStatus;
import com.tmt.TMLibrary.common.redis.RedisKeys;
import com.tmt.TMLibrary.dto.redis.CaptchaRedis;
import com.tmt.TMLibrary.dto.request.LoginRequest;
import com.tmt.TMLibrary.dto.response.LoginResponse;
import com.tmt.TMLibrary.entity.User;
import com.tmt.TMLibrary.exception.AuthException;
import com.tmt.TMLibrary.exception.BusinessException;
import com.tmt.TMLibrary.mapper.UserMapper;
import com.tmt.TMLibrary.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthServiceImpl.login 核心路径单测 —— 覆盖登录成功/失败分支,并守住所修的
 * 「lastLoginTime + lastLoginIp 两笔 DB 写在同一个事务里」边界:
 *   成功 → 两笔都写;失败(密码错/用户不存在/验证码错)→ 两笔都不写。
 */
class AuthServiceImplTest {

    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private UserMapper userMapper;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private ObjectMapper objectMapper;
    private AuthServiceImpl authService;

    private static final String USERNAME = "demo_admin";
    private static final String PASSWORD = "Demo@123456";
    private static final String CAPTCHA = "1234";
    private static final String UUID = "uuid-abc-123";
    private static final String IP = "127.0.0.1";

    @BeforeEach
    void setUp() {
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        userMapper = mock(UserMapper.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        objectMapper = new ObjectMapper();
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        authService = new AuthServiceImpl(passwordEncoder, jwtService, userMapper, redisTemplate, objectMapper);
    }

    private String captchaJson() throws Exception {
        CaptchaRedis c = new CaptchaRedis();
        c.setCaptcha(CAPTCHA);
        c.setUsername(USERNAME);
        c.setExpiresAt(System.currentTimeMillis() + 180_000);
        return objectMapper.writeValueAsString(c);
    }

    private User activeUser() {
        User u = new User();
        u.setId(5);
        u.setUsername(USERNAME);
        u.setPasswordHash("hash");
        u.setStatus(UserStatus.ACTIVE.getCode());
        u.setRole(UserRole.ADMIN.getCode());
        return u;
    }

    private LoginRequest loginRequest() {
        LoginRequest req = new LoginRequest();
        req.setUsername(USERNAME);
        req.setPassword(PASSWORD);
        req.setCaptcha(CAPTCHA);
        req.setUuid(UUID);
        req.setIpAddress(IP);
        return req;
    }

    @Test
    void loginSuccess_writesBothLastLoginColumns_andReturnsToken() throws Exception {
        // captcha 命中 Redis(第 1 次 get),用户缓存 miss(第 2 次 get → null → 走 DB)
        when(valueOps.get(anyString())).thenReturn(captchaJson(), null);
        when(userMapper.selectUserByUsername(USERNAME)).thenReturn(activeUser());
        when(userMapper.getAccountLockedUntilById(anyInt())).thenReturn(null);
        when(passwordEncoder.matches(PASSWORD, "hash")).thenReturn(true);
        when(jwtService.issue(any(User.class), anyString())).thenReturn("jwt-token");

        LoginResponse resp = authService.login(loginRequest());

        assertEquals("jwt-token", resp.getToken());
        assertEquals(USERNAME, resp.getUsername());
        assertEquals(UserRole.ADMIN, resp.getRole());

        // 核心:两笔 lastLogin DB 写都发生,保持同一事务边界
        verify(userMapper).setLastLoginTimeById(eq(5), any(LocalDateTime.class));
        verify(userMapper).setLastLoginIpById(5, IP);
        // 成功后验证码删除,防止同图重放
        verify(redisTemplate).delete(RedisKeys.captchaLogin(UUID));
    }

    @Test
    void login_wrongPassword_throwsUnauthorized_andWritesNoLastLogin() throws Exception {
        when(valueOps.get(anyString())).thenReturn(captchaJson(), null);
        when(userMapper.selectUserByUsername(USERNAME)).thenReturn(activeUser());
        when(userMapper.getAccountLockedUntilById(anyInt())).thenReturn(null);
        when(passwordEncoder.matches(PASSWORD, "hash")).thenReturn(false);

        assertThrows(AuthException.class, () -> authService.login(loginRequest()));

        // 失败路径:两笔 lastLogin DB 写都不应发生
        verify(userMapper, never()).setLastLoginTimeById(anyInt(), any(LocalDateTime.class));
        verify(userMapper, never()).setLastLoginIpById(anyInt(), anyString());
        // 失败会原子递增计数
        verify(userMapper).incrementAndMaybeLock(anyInt(), anyInt(), anyInt());
    }

    @Test
    void login_unknownUser_throwsUnauthorized_andWritesNoLastLogin() throws Exception {
        when(valueOps.get(anyString())).thenReturn(captchaJson(), null);
        when(userMapper.selectUserByUsername(USERNAME)).thenReturn(null);

        assertThrows(AuthException.class, () -> authService.login(loginRequest()));

        verify(userMapper, never()).setLastLoginTimeById(anyInt(), any(LocalDateTime.class));
        verify(userMapper, never()).setLastLoginIpById(anyInt(), anyString());
    }

    @Test
    void login_wrongCaptcha_throwsBadRequest_andWritesNoLastLogin() throws Exception {
        // captcha 值里的验证码与请求不符 → 在校验阶段直接抛
        CaptchaRedis c = new CaptchaRedis();
        c.setCaptcha("9999");
        c.setUsername(USERNAME);
        c.setExpiresAt(System.currentTimeMillis() + 180_000);
        String json = objectMapper.writeValueAsString(c);
        when(valueOps.get(anyString())).thenReturn(json);

        assertThrows(BusinessException.class, () -> authService.login(loginRequest()));

        // 未通过验证码校验,不会走到用户缓存/DB 查询,更不会写 lastLogin
        verify(userMapper, never()).setLastLoginTimeById(anyInt(), any(LocalDateTime.class));
        verify(userMapper, never()).setLastLoginIpById(anyInt(), anyString());
        verify(userMapper, never()).selectUserByUsername(anyString());
    }

    @Test
    void login_missingCaptcha_throwsNotFound() {
        when(valueOps.get(anyString())).thenReturn(null);

        assertThrows(BusinessException.class, () -> authService.login(loginRequest()));
    }

    /**
     * 守门测试:login 方法必须带 @Transactional —— 否则 lastLoginTime/lastLoginIp
     * 两笔 DB 写就不在同一个事务里,回到"半写"缺陷。
     */
    @Test
    void login_isTransactional_guardingTheTwoWriteAtomicity() throws Exception {
        java.lang.reflect.Method m = AuthServiceImpl.class.getMethod("login", LoginRequest.class);
        org.springframework.transaction.annotation.Transactional tx =
            m.getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        assertNotNull(tx, "login 必须标注 @Transactional,才能保证 lastLogin 两笔写同进同退");
        // 用 rollbackFor = Exception.class(数组),避免运行时异常只部分提交
        assertEquals(1, tx.rollbackFor().length);
        assertEquals(Exception.class, tx.rollbackFor()[0]);
    }
}