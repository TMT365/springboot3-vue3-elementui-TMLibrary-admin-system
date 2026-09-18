package com.tmt.TMLibrary.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 凭令牌重置密码 —— POST /api/users/reset-password
 */
@Data
public class ResetPasswordRequest {

    /** 邮件链接里带过来的明文令牌(服务端比对的是它的 SHA-256) */
    @NotBlank(message = "重置令牌不能为空")
    @Size(max = 128, message = "重置令牌格式不正确")
    private String token;

    /** 长度上下限与注册/改密保持一致,避免出现"能重置成注册不允许的密码" */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度不能小于6位且不能大于20位")
    private String newPassword;
}
