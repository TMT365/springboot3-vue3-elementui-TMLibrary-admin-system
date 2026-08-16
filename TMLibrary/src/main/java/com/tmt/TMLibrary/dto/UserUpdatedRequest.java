package com.tmt.TMLibrary.dto;

import com.tmt.TMLibrary.common.User.UserRole;
import com.tmt.TMLibrary.common.User.UserStatus;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * @brief PATCH /api/users/{id} 的 body。
 *
 * <p>这里面的字段都是可选的 — 用户可以选择性地更新某些字段,不必全填。
 *
 * <p>不应包含的安全/审计字段(由 Service 内部维护,不暴露给前端):
 * <ul>
 *   <li>password — 改密走专用 /password 端点</li>
 *   <li>salt / passwordHash / passwordResetToken* — BCrypt/重置机制内部字段</li>
 *   <li>lastLoginTime / lastLoginIp — 由登录链路自动写</li>
 *   <li>failedLoginAttempts / accountLockedUntil — 由锁定机制自动维护</li>
 *   <li>deletedAt — 软删走 DELETE 端点</li>
 *   <li>updateTime — DB ON UPDATE CURRENT_TIMESTAMP 自动刷</li>
 *   <li>id — 走 URL @PathVariable,不允许 body 覆盖</li>
 * </ul>
 *
 * <p>role / status 由 Service 校验谁能改(BOSS 改 role,ADMIN+ 改 status)。
 */
@Getter
@Setter
public class UserUpdatedRequest extends UserCommonRequest {

    @Size(min = 3, max = 50, message = "用户名长度 3-50")
    private String username;

    @Email(message = "邮箱格式不正确")
    private String email;

    @Size(max = 20, message = "手机号长度不能超过 20")
    private String phoneNumber;

    // 这两个字段只有 ADMIN/BOSS 才能修改,Service 校验
    private UserStatus status;
    private UserRole role;
}