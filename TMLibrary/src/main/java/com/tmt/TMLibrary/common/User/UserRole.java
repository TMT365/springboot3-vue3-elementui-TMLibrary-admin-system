package com.tmt.TMLibrary.common.User;

import com.tmt.TMLibrary.exception.BusinessException;
import lombok.Getter;
import com.tmt.TMLibrary.common.Result.ResultCode;

/**
 * @brief 这个类定义了用户角色的枚举类型，用于表示不同的用户权限和访问级别。
 * @author tmt
 * @version 1.0
 * @since 2026-08-14
 * @see com.tmt.TMLibrary.service.UserManagementService
 * UserRole
 */
@Getter
public enum UserRole {
    USER(0, "普通用户"),
    ADMIN(1, "管理员"),
    BOSS(2, "老板");

    private final Integer code;
    private final String description;

    UserRole(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static UserRole fromString(String role) {
        for (UserRole userRole : UserRole.values()) {
            if (userRole.name().equalsIgnoreCase(role)) {
                return userRole;
            }
        }
        throw new BusinessException(ResultCode.NOT_FOUND, "Unknown role: " + role);
    }

    public static UserRole getUserRoleByCode(Integer code) {
        for (UserRole role : UserRole.values()) {
            if (role.getCode().equals(code)) {
                return role;
            }
        }
        throw new BusinessException(ResultCode.NOT_FOUND, "Unknown role code: " + code);
    }
}
