package com.tmt.TMLibrary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class UserRegisterRequest {
    @NotBlank(message = "Email cannot be blank") // 添加非空验证注解
    @Email(message = "Email should be valid") // 添加邮箱格式验证注解
    private String email; // 用户的邮箱地址


    @NotBlank(message = "Phone number cannot be blank") // 添加非空验证注解
    @Size(min = 11, max = 11, message = "Phone number should be exactly 11 characters") // 添加手机号长度验证注解
    private String phoneNumber; // 用户的手机号


    @NotBlank(message = "Password cannot be blank") // 添加非空验证注解
    @Size(min = 6, max = 20, message = "Password should be at least 6 characters and at most 20 characters") // 添加密码长度验证注解
    private String password;

    
    @NotBlank(message = "Username cannot be blank") // 添加非空验证注解
    @Size(min = 3, max = 50, message = "Username should be at least 3 characters and at most 50 characters") // 添加用户名长度验证注解
    private String username;
}
