package com.tmt.TMLibrary.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 申请密码重置 —— POST /api/users/forgot-password
 *
 * <p>用户填的是**邮箱**。之所以不用用户名:重置链接要发到邮箱,
 * 让用户填邮箱就省掉一次"用户名 → 邮箱"的间接映射,也避免用户
 * 记错用户名时收到"账号不存在"这种没必要的提示。</p>
 */
@Data
public class ForgotPasswordRequest {

    @NotBlank(message = "邮箱不能为空")
    @Size(max = 100, message = "邮箱长度不能超过 100")
    private String email;
}
