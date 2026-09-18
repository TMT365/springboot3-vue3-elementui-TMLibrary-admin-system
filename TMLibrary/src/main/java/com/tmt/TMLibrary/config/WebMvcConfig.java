package com.tmt.TMLibrary.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.tmt.TMLibrary.security.context.CurrentUserArgumentResolver;

import lombok.RequiredArgsConstructor;

/**
 * Spring MVC 通用配置。
 *
 * <p>
 * 当前仅注册 CORS：放行前端 dev server (Vue Vite, 默认 :5173) 对 {@code /**}
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
     * 多个 origin / origin-pattern 用逗号分隔。
     *
     * <p>支持两种写法(都用 {@code allowedOriginPatterns} 匹配,精确值也走同一 API):</p>
     * <ul>
     *   <li>精确 origin:{@code http://localhost:5173}</li>
     *   <li>带通配符的 pattern:{@code http://localhost:[*]}(任意端口)、
     *       {@code https://*.example.com}</li>
     * </ul>
     *
     * <p><b>为什么用 pattern 而不是精确 origin</b>:vite dev server 端口会漂移
     * (5173 被占用就自动换 5174、5175…),精确白名单每次都要改配置 + 重启后端,
     * 表现为前端收到满屏 {@code 403 Invalid CORS request}。
     * {@code localhost:[*]} 只匹配本机来源,网站域名永远匹配不上,dev 场景安全;
     * <b>生产环境务必用 app.cors.origins 覆盖成精确域名</b>。</p>
     */
    @Value("${app.cors.origins:http://localhost:[*]}")
    private String allowedOrigins;

    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // 用 allowedOriginPatterns:兼容精确 origin,同时支持 [*] 端口通配
                .allowedOriginPatterns(allowedOrigins.split(","))
                // ⚠️ PATCH 必须在这里 —— 全项目有 6 个 @PatchMapping(改图书/改库存/改用户/
                // 改密码/改反馈状态/支付),漏掉它的话,跨域场景下这些请求的预检
                // (OPTIONS)会被 Spring 判为 "Invalid CORS request" 直接 403,
                // 而 GET/POST 一切正常 —— 表现成"只有支付用不了"这种极难查的症状。
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
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