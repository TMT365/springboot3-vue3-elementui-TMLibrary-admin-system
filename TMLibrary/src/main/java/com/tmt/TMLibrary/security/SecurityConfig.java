package com.tmt.TMLibrary.security;

import com.tmt.TMLibrary.security.jwt.JwtAuthFilter;
import com.tmt.TMLibrary.security.jwt.JwtProperties;
import com.tmt.TMLibrary.security.jwt.JwtService;
import com.tmt.TMLibrary.service.IpBanService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;


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
     * 自己注册一个 JwtAuthFilter &#64;Bean,控制 URL pattern + 顺序。
     *
     * @param jwtService JWT 解析服务
     * @param errorWriter 错误响应写入器
     * @param stringRedisTemplate Redis 模板(JWT 黑名单)
     * @return JwtAuthFilter 实例
     */
    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtService jwtService, AuthErrorWriter errorWriter, StringRedisTemplate stringRedisTemplate) {
        return new JwtAuthFilter(jwtService, errorWriter, stringRedisTemplate);
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
        reg.addUrlPatterns("/api/*");
        reg.setOrder(10);
        reg.setName("jwtAuthFilter");
        return reg;
    }

    /**
     * IP 风控过滤器 —— 同样不带 @Component,由这里显式注册。
     *
     * <p>order 5:排在 JwtAuthFilter(10)之前 —— 已封禁的 IP 连 token 都不解析,
     * 直接 429 打回,省掉后续解析/查库开销。也在登录验证码等白名单接口之前,
     * 所以脚本刷验证码同样会被拦。</p>
     */
    @Bean
    public IpRiskControlFilter ipRiskControlFilter(
            IpBanService ipBanService,
            AuthErrorWriter errorWriter,
            @Value("${app.security.ip-ban.trust-private-ips:true}") boolean trustPrivateIps,
            @Value("${app.security.ip-ban.trusted-proxies:}") String trustedProxiesCsv) {
        return new IpRiskControlFilter(ipBanService, errorWriter, trustPrivateIps,
                parseTrustedProxies(trustedProxiesCsv));
    }

    /**
     * 解析可信代理名单 —— 逗号分隔,空白项丢弃。
     *
     * <p>空串 → 空集合 = <b>不信任任何代理头</b>。这是刻意的默认值:
     * 直连部署下 {@code remoteAddr} 就是真实来源,读 {@code X-Forwarded-For}
     * 只会给伪造者可乘之机(见 {@code IpRiskControlFilter.resolveClientIp})。</p>
     */
    private static Set<String> parseTrustedProxies(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Bean
    public FilterRegistrationBean<IpRiskControlFilter> ipRiskControlFilterRegistration(
            IpRiskControlFilter filter) {
        FilterRegistrationBean<IpRiskControlFilter> reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/api/*");
        reg.setOrder(5);
        reg.setName("ipRiskControlFilter");
        return reg;
    }

}
