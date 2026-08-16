package com.tmt.TMLibrary.dto;

import jakarta.validation.constraints.NotNull;

/**
 * @brief 用户通用请求类，用于封装用户操作所需的基本信息。
 */
public class UserCommonRequest {
    @NotNull(message = "User ID cannot be null") // 添加非空验证注解
    private Integer id; // 用户的唯一标识符，用于数据库操作

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }


    public String toString() {
        return "UserCommonRequest{" +
                "id=" + id +
                '}';
    }
}
