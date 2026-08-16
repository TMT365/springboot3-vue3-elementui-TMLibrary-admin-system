package com.tmt.TMLibrary.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 安全相关 Bean 集中配置。
 * <p>当前只暴露 PasswordEncoder — BCrypt 是默认算法,
 * cost 因子 10(每次哈希 ~80ms,符合 OWASP 推荐的 ≥ 2024 标准)。
 * BCryptPasswordEncoder 自带随机 salt,匹配时自动从 hash 字符串里提取,
 * 不需要单独存储/读取 salt。
 */
@Configuration
public class SecurityConfig {
    /**
     * PasswordEncoder 是线程安全的 Bean,整个应用共享一个实例即可。
     * UserServiceImpl 通过构造器注入使用。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
