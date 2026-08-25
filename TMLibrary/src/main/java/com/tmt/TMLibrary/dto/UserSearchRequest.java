package com.tmt.TMLibrary.dto;

import java.time.LocalDateTime;

import com.tmt.TMLibrary.common.User.UserRole;
import com.tmt.TMLibrary.common.User.UserStatus;

import lombok.Data;

@Data
public class UserSearchRequest {
    private String username; // LIKE 查询用户名
    // role 不加 @NotBlank — 查询时不传 role 也合法,compact() 会兜底默认 USER
    private UserRole role; // 精确

    private LocalDateTime createdTimeStart; // 区间查询
    private LocalDateTime createdTimeEnd; // 区间查询

    private LocalDateTime updatedTimeStart; // 区间查询
    private LocalDateTime updatedTimeEnd; // 区间查询

    private LocalDateTime lastLoginTimeStart; // 区间查询
    private LocalDateTime lastLoginTimeEnd; // 区间查询

    private String lastLoginIp; // 精确 （IP 地址）
    private int failedLoginAttempts; // 精确

    private LocalDateTime accountLockedUntilStart; // 区间查询
    private LocalDateTime accountLockedUntilEnd; // 区间查询

    private UserStatus status; // 精确

    private LocalDateTime deletedAtStart; // 区间查询
    private LocalDateTime deletedAtEnd; // 区间查询

    private String phoneNumber; // LIKE 查询手机号

    // 查询时的分页参数
    private Integer page = 1;
    private Integer size = 10;

    /**
     * @brief 压缩查询条件，去除空值和无效值, 这就相当于是一种预处理，避免在查询时传入无效的参数
     */
    public void compact() {
        // 如果 username 或 phoneNumber 是空字符串，则将其置为 null，表示不进行该条件的查询
        if (username != null && username.isBlank())
            username = null;
        if (phoneNumber != null && phoneNumber.isBlank())
            phoneNumber = null;

        if (page == null || page <= 0)
            page = 1;
        if (size == null || size <= 0)
            size = 10;
        if (size > 100)
            size = 100;

        if (createdTimeStart != null && createdTimeEnd != null && createdTimeStart.isAfter(createdTimeEnd)) {
            // 如果开始时间晚于结束时间，则全部置为 null，表示不进行时间范围查询
            createdTimeStart = null;
            createdTimeEnd = null;
        }

        if (updatedTimeStart != null && updatedTimeEnd != null && updatedTimeStart.isAfter(updatedTimeEnd)) {
            updatedTimeStart = null;
            updatedTimeEnd = null;
        }

        if (lastLoginTimeStart != null && lastLoginTimeEnd != null && lastLoginTimeStart.isAfter(lastLoginTimeEnd)) {
            lastLoginTimeStart = null;
            lastLoginTimeEnd = null;
        }

        if (accountLockedUntilStart != null && accountLockedUntilEnd != null && accountLockedUntilStart.isAfter(accountLockedUntilEnd)) {
            accountLockedUntilStart = null;
            accountLockedUntilEnd = null;
        }

        if (deletedAtStart != null && deletedAtEnd != null && deletedAtStart.isAfter(deletedAtEnd)) {
            deletedAtStart = null;
            deletedAtEnd = null;
        }

        if (failedLoginAttempts < 0)
            failedLoginAttempts = 0;

        if (role == null)
            role = UserRole.USER; // 默认查询普通用户

        if (status == null)
            status = UserStatus.ACTIVE; // 默认查询激活状态的用户

        // 如果 lastLoginIp 是空字符串，则将其置为 null，表示不进行该条件的查询
        if (lastLoginIp != null && lastLoginIp.isBlank())
            lastLoginIp = null;
    }
}
