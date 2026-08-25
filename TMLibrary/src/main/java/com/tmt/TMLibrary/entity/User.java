package com.tmt.TMLibrary.entity;

import java.time.LocalDateTime;
import lombok.Data;
import com.tmt.TMLibrary.common.User.UserRole;
import com.tmt.TMLibrary.common.User.UserStatus;

@Data
public class User {
    private int id;
    private String username;
    private String realName;
    private String passwordHash;
    private String email;
    private String avatarUrl;
    private Integer status; // 用户状态，使用枚举类型

    private Integer role; // 用户角色，使用枚举类型
    private String phoneNumber; // 用户的电话号码
    private LocalDateTime createdTime; // 记录创建时间
    private LocalDateTime updatedTime; // 记录更新时间
    private LocalDateTime lastLoginTime; // 记录最后登录时间
    private String lastLoginIp; // 记录最后登录IP地址
    private int failedLoginAttempts; // 记录连续登录失败的次数
    private LocalDateTime accountLockedUntil; // 记录账户被锁定的时间，超过这个时间后才能再次尝试登录
    private String passwordResetToken; // 用于密码重置的令牌
    private LocalDateTime passwordResetTokenExpiration; // 记录密码重置令牌的过期时间
    private LocalDateTime deletedAt; // 记录用户被删除的时间，用于软删除
    public String getPassword() {
        return passwordHash;
    }
}
