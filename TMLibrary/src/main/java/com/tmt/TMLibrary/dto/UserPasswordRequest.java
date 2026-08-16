package com.tmt.TMLibrary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @brief PATCH /api/users/{id}/password 的 body。
 * 必须 currentUserId == id(只能改自己的密码)。
 */
@Data
public class UserPasswordRequest {

    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "新密码长度 6-20")
    private String newPassword;
}