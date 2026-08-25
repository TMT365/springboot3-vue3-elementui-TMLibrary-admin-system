package com.tmt.TMLibrary.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.tmt.TMLibrary.security.CurrentUserArgumentResolver;

import lombok.RequiredArgsConstructor;

/**
 * Spring MVC 通用配置。
 *
 * <p>
 * 当前仅注册 CORS：放行前端 dev server (Vue Vite, 默认 :5173) 对 {@code /api/**}
 * 的跨域请求。Spring Security 等拦截器未引入，所以直接用 {@code WebMvcConfigurer}
 * 即可，不需要 filter chain。
 * </p>
 *
 * <p>
 * 允许的 origin 通过 {@code app.cors.origins} 配置(逗号分隔),默认
 * {@code http://localhost:5173}。
 * 生产环境按域名收敛,不要用 {@code *} —— 那会连 credentials 一起被禁掉。
 * </p>
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 多个 origin 用逗号分隔,例如: "http://localhost:5173,https://admin.example.com"
     */
    @Value("${app.cors.origins:http://localhost:5173}")
    private String allowedOrigins;

    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type")
                .allowCredentials(false)
                .maxAge(3600);
    }

    /** 🆕 新增:注册 @CurrentUser 解析器 */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }
}