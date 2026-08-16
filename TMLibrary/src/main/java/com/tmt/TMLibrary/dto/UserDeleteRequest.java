package com.tmt.TMLibrary.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @brief DELETE /api/users/{id} 的 body,只放密码。
 * id 走 URL(@PathVariable),不放 body。
 * 继承 UserCommonRequest 没意义(password 不是公共字段)。
 */
@Data
public class UserDeleteRequest {

    @NotBlank(message = "请输入当前密码以确认删除操作")
    private String password;
}