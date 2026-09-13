package com.tmt.TMLibrary.vo;

import java.time.LocalDateTime;
import com.tmt.TMLibrary.entity.User;
import lombok.Getter;

@Getter
public class UserVo {
    private int id;
    private String username;
    // 放回形式————姓氏**
    private String realName;

    private String email;
    private String avatarUrl;

    private Integer status; // 用户状态，使用枚举类型的code

    private Integer role; // 用户角色，使用枚举类型code

    private String phoneNumber; // 用户的电话号码
    private LocalDateTime createdTime; // 记录创建时间
    private LocalDateTime updatedTime; // 记录更新时间
    private LocalDateTime lastLoginTime; // 记录最后登录时间
    private String lastLoginIp; // 记录最后登录IP地址
    private int failedLoginAttempts; // 记录连续登录失败的次数
    private LocalDateTime accountLockedUntil; // 记录账户被锁定的时间，超过这个时间后才能再次尝试登录

    private LocalDateTime deletedAt; // 记录用户被删除的时间，用于软删除

    public static UserVo fromUser(User user) {
        UserVo vo = new UserVo();
        vo.id = user.getId();
        vo.username = user.getUsername();
        vo.realName = vo.encryptInformation(user.getRealName(), 1);
        vo.email = vo.maskEmail(user.getEmail());
        vo.avatarUrl = user.getAvatarUrl();
        vo.role = user.getRole();
        vo.status = user.getStatus();
        vo.createdTime = user.getCreatedTime();
        vo.updatedTime = user.getUpdatedTime();
        vo.lastLoginTime = user.getLastLoginTime();
        vo.lastLoginIp = user.getLastLoginIp();
        vo.failedLoginAttempts = user.getFailedLoginAttempts();
        vo.accountLockedUntil = user.getAccountLockedUntil();
        vo.deletedAt = user.getDeletedAt();
        vo.phoneNumber = vo.encryptInformation(user.getPhoneNumber(), 7);

        return vo;
    }

    private String encryptInformation(String ifo, int keepIndex) {
        if (ifo == null) {
            return "";
        }
        // 防止 keepIndex > 长度时 substring 越界
        if (ifo.length() <= keepIndex) {
            return ifo;
        }
        return ifo.substring(0, keepIndex) + "*".repeat(ifo.length() - keepIndex);
    }

    /**
     * 邮箱脱敏 — 本地部分保留首字符,域名完整保留。
     * <pre>
     *   alice@example.com   → a****@example.com
     *   ab@example.com      → a*@example.com
     *   a@example.com       → a@example.com(单字符无法再脱敏)
     * </pre>
     */
    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            // 非法邮箱(无 @)— 整体按普通敏感字段处理
            return encryptInformation(email, 1);
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() == 1) {
            return email; // 单字符本地部分保留原样
        }
        return local.charAt(0) + "*".repeat(local.length() - 1) + domain;
    }
}
