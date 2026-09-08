package com.tmt.TMLibrary.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户的登录请求数据传输转换层
 * LoginRequest
 */
@Getter
@Setter
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度不能小于6位且不能大于20位")
    private String password;

    @NotBlank(message="验证码不能为空")
    private String captcha;

    // 前端自己生成的必带
    @NotBlank(message = "uuid不能为空")
    private String uuid;

    // 这个用户不需要自己上传，因为登录请求的IP地址可以通过后端获取到，所以不需要前端传递过来。后端可以在接收到登录请求时，通过HttpServletRequest对象获取到客户端的IP地址，然后将其存储在LoginRequest对象中，或者直接在AuthServiceImpl中获取IP地址并存储到数据库中。
    private String ipAddress; // 新增字段，用于存储登录请求的IP地址

    public String toString() {
        return "LoginRequest{" +
                "username='" + username + '\'' +
                ", password='" + null + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                '}';
    }
}
