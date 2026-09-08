package com.tmt.TMLibrary.security.jwt;

import java.util.List;

import com.tmt.TMLibrary.security.AuthErrorWriter;
import com.tmt.TMLibrary.security.context.CurrentUserContext;
import com.tmt.TMLibrary.security.context.UserView;
import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.tmt.TMLibrary.exception.AuthException;
import java.io.IOException;
import org.springframework.data.redis.core.StringRedisTemplate;


// 不要加 @Component!由 SecurityConfig 的 FilterRegistrationBean 显式注册,避免被默认 servlet 注册一次 + 这里再注册一次。
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final List<String> WHITELIST = List.of(
            "/api/users/login",
            "/api/users/register",
            "/api/captcha/");

    private final JwtService jwtService;
    private final AuthErrorWriter errorWriter;
    private final StringRedisTemplate stringRedisTemplate;
    // 命名风格：tmlibrary:{domain}:{feature}:{keyId}
    // 与 PurchaseService 的 tmlibrary:user:{userId}:xxx 区分（domain 段不同）
    private static final String REDIS_JWT_BLACK_PATH = "tmlibrary:auth:jwt:blackList:";

    public JwtAuthFilter(JwtService jwtService, AuthErrorWriter errorWriter, StringRedisTemplate stringRedisTemplate) {
        this.jwtService = jwtService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, @NonNull HttpServletResponse resp,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String path = req.getRequestURI();

        // ① OPTIONS 预检放行(CORS 后续处理)
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            chain.doFilter(req, resp);
            return;
        }

        // ② 白名单放行
        if (isWhitelisted(path)) {
            chain.doFilter(req, resp);
            return;
        }

        // ③ 拿 Authorization 头
        String header = req.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            errorWriter.writeAuthError(resp, "缺少 Authorization 头");
            return; // ❌ 不调 chain.doFilter
        }

        String token = header.substring(7).trim();

        // ④ parse + 写 attribute
        try {
            Claims claims = jwtService.parse(token);

            // 判断当前用户的 JWT token 的 jti 是否在缓存里面
            String jti = claims.getId();
            String blackKey = REDIS_JWT_BLACK_PATH + jti;
            if(Boolean.TRUE.equals(stringRedisTemplate.hasKey(blackKey))){
                //jti在黑名单，token已经登出作废，直接拦截
                errorWriter.writeAuthError(resp,"Token已登出作废，请重新登录");
                return;
            }

            // 把 claims 转成 UserView,写 request attribute
            UserView user = new UserView();
            user.setId(claims.get("uid", Integer.class));
            user.setUsername(claims.getSubject());
            user.setRole(claims.get("role", Integer.class));

            CurrentUserContext.set(req, user); // 静态 helper
            chain.doFilter(req, resp); // ✅ 放行

        } catch (AuthException e) {
            errorWriter.writeAuthError(resp, e.getMessage());
        } catch (Exception e) { // 兜底
            errorWriter.writeAuthError(resp, "令牌无效");
        }
    }

    /**
     * 这里使用 .stream()是一种快速的处理数据的方式。等价于使用最基础的for循环
     * <pre>
     * for (int i = 0; i < WHITELIST.size(); i++) {
     *     if (path.startsWith(WHITELIST.get(i))) {
     *         return true;
     *     }
     * }
     * return false;
     * </pre>
     */

    private boolean isWhitelisted(String path) {
        return WHITELIST.stream().anyMatch(path::startsWith);
    }
}
