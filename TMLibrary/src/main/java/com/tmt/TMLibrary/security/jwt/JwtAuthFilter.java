package com.tmt.TMLibrary.security.jwt;

import java.util.List;
import java.util.Set;

import com.tmt.TMLibrary.common.redis.RedisKeys;
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

    /**
     * 免鉴权规则。
     *
     * <p><b>为什么要带 HTTP 方法</b>:图书模块的读接口(商城/详情页,GET)必须对未登录
     * 访客开放,而同一路径前缀下的写接口(新建/修改/删除图书、盘点调整库存)
     * 必须要求 token。若只按路径前缀放行 {@code /api/books},写操作会被一起放行。</p>
     *
     * @param path   路径(精确匹配)或前缀
     * @param methods 允许的方法;空集合表示不限方法
     * @param exact  true=精确匹配,false=前缀匹配
     */
    private record WhitelistRule(String path, Set<String> methods, boolean exact) {
        boolean matches(String requestPath, String requestMethod) {
            if (!methods.isEmpty() && !methods.contains(requestMethod)) {
                return false;
            }
            return exact ? path.equals(requestPath) : requestPath.startsWith(path);
        }
    }

    private static final List<WhitelistRule> WHITELIST = List.of(
            // 认证入口:仅 POST
            new WhitelistRule("/api/users/login", Set.of("POST"), true),
            new WhitelistRule("/api/users/register", Set.of("POST"), true),
            // 验证码:POST 取图(login / register 两个端点),前缀匹配
            new WhitelistRule("/api/captcha/", Set.of("POST"), false),
            // 图书展示:所有 GET 放行(未登录可浏览商城/图书列表/详情/搜索),
            // 同一前缀下的 POST/PATCH/DELETE(新建、修改、删书、盘点调库存)仍需 token
            new WhitelistRule("/api/books", Set.of("GET"), false));

    private final JwtService jwtService;
    private final AuthErrorWriter errorWriter;
    private final StringRedisTemplate stringRedisTemplate;
    // 黑名单 key 由 RedisKeys 统一管理

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
        if (isWhitelisted(path, req.getMethod())) {
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
            String blackKey = RedisKeys.jwtBlacklist(jti);
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
     * 判断请求是否免鉴权。
     *
     * @param path   请求路径(不含 query string)
     * @param method HTTP 方法,如 GET / POST
     */
    private boolean isWhitelisted(String path, String method) {
        return WHITELIST.stream().anyMatch(rule -> rule.matches(path, method));
    }
}
