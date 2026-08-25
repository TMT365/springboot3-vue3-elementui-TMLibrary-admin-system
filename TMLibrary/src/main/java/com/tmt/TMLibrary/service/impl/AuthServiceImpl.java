package com.tmt.TMLibrary.service.impl;

import com.tmt.TMLibrary.common.User.UserRole;
import com.tmt.TMLibrary.service.AuthService;
import com.tmt.TMLibrary.dto.LoginResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.tmt.TMLibrary.dto.LoginRequest;
import com.tmt.TMLibrary.security.JwtService;
import com.tmt.TMLibrary.entity.User;
import com.tmt.TMLibrary.exception.AuthException;
import com.tmt.TMLibrary.mapper.UserMapper;
import org.springframework.stereotype.Service;
import com.tmt.TMLibrary.common.Result.ResultCode;
import com.tmt.TMLibrary.common.User.UserStatus;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 用户登录成功后，不仅是返回一个token和需要返回用户的角色信息，而且还需要对{@link com.tmt.TMLibrary.entity.User}的其它不准用户请求的敏感信息进行过滤，避免返回给前端。这里我们使用{@link com.tmt.TMLibrary.dto.LoginResponse}来封装返回给前端的用户信息。
 * 
 * @Classname {@link AuthServiceImpl}
 */
@Service
public class AuthServiceImpl implements AuthService {
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    // Autowired constructor injection for JwtService and UserMapper
    public AuthServiceImpl(PasswordEncoder passwordEncoder, JwtService jwtService, UserMapper userMapper) {
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
    }
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public LoginResponse login(LoginRequest loginRequest) {
        User user = userMapper.selectUserByUsername(loginRequest.getUsername());
        if (user == null) {
            throw new AuthException(ResultCode.UNAUTHORIZED, "用户不存在");
        } 
        // 判断用户状态是否为ACTIVE 或是 账户是否被锁定
        if (!user.getStatus().equals(UserStatus.ACTIVE.getCode())) {
            throw new AuthException(ResultCode.FORBIDDEN, "用户账户未激活");
        }
        if ((user.getAccountLockedUntil() != null && user.getAccountLockedUntil().isAfter(LocalDateTime.now()))){
            throw new AuthException(ResultCode.FORBIDDEN, "用户账户已被锁定，请稍后再试，解除锁时间：" + user.getAccountLockedUntil().format(TIME_FORMATTER));
        }
        // 判断是否被软删除
        if (user.getDeletedAt() != null) {
            throw new AuthException(ResultCode.FORBIDDEN, "用户账户已被删除");
        }
        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())){
            // 密码错误，增加连续登录失败次数
            userMapper.incrementFailedLoginAttempts(user.getId());
            // 如果连续登录失败次数超过阈值，锁定账户
            if (user.getFailedLoginAttempts() + 1 >= 5) { // �假设阈值为5次
                LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(15); // 锁定15分钟
                userMapper.lockUserAccount(user.getId(), lockUntil);
                throw new AuthException(ResultCode.FORBIDDEN, "用户账户已被锁定，请稍后再试，解除锁时间：" + lockUntil.format(TIME_FORMATTER));
            }
            throw new AuthException(ResultCode.UNAUTHORIZED, "密码错误");
        }
        // 登录成功，重置连续登录失败次数
        userMapper.resetFailedLoginAttempts(user.getId());
        // 更新用户的最后登录时间、IP地址
        // 更新 lastLoginTime 和 lastLoginIp
        userMapper.setLastLoginTime(user.getId(), LocalDateTime.now());
        userMapper.setLastLoginIp(user.getId(), loginRequest.getIpAddress());
        // 生成JWT token
        String token = jwtService.issue(user);
        return new LoginResponse(token, user.getUsername(), UserRole.getUserRoleByCode(user.getRole()));
    }
}
