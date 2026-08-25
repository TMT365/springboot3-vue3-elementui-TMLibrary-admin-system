package com.tmt.TMLibrary.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;


/**
 * 安全相关 Bean 集中配置。
 * <p>
 * 当前只暴露 PasswordEncoder — BCrypt 是默认算法,
 * cost 因子 10(每次哈希 ~80ms,符合 OWASP 推荐的 ≥ 2024 标准)。
 * BCryptPasswordEncoder 自带随机 salt,匹配时自动从 hash 字符串里提取,
 * 不需要单独存储/读取 salt。
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    /**
     * PasswordEncoder 是线程安全的 Bean,整个应用共享一个实例即可。
     * UserServiceImpl 通过构造器注入使用。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 自己注册一个 JwtAuthFilter @Bean,控制 URL pattern + 顺序。
     * 
     * @param filter
     * @return
     */
    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtService jwtService, AuthErrorWriter errorWriter) {
        return new JwtAuthFilter(jwtService, errorWriter);
    }

    /**
     * 显式注册 JwtAuthFilter,控制 URL pattern + 顺序。
     * <p>
     * setOrder(10):Spring 内置 filter 之后(CharacterEncodingFilter=1,
     * RequestContextFilter=5)
     * ,确保 CORS preflight / RequestContext 已经准备好。
     * <p>
     * JwtAuthFilter 本身不带 @Component(否则 Spring 会默认注册一次 + 这里再注册一次,双跑)。
     */
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/*");
        reg.setOrder(10);
        reg.setName("jwtAuthFilter");
        return reg;
    }

}
