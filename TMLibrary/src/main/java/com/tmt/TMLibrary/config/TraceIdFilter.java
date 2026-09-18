package com.tmt.TMLibrary.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 给每个请求打一个 <b>traceId</b>,写进 MDC —— 日志里就能把一次请求的所有行串起来。
 *
 * <h2>为什么这是"企业日志"的分水岭</h2>
 * <p>没有 traceId 的日志只能按时间戳猜:"09:38:10 有个 500" —— 但同一秒可能有几十个请求,
 * 你没法知道哪几行属于同一次调用。有了它,一行 {@code grep <traceId>} 就能还原完整链路:</p>
 * <pre>
 * 09:38:10.317 INFO  [a1b2c3d4e5f6] [http-nio-8080-exec-1] c.t.T.c.BookController : 前端请求 /books
 * 09:38:10.318 INFO  [a1b2c3d4e5f6] [http-nio-8080-exec-1] c.t.T.s.i.BookServiceImpl : 命中缓存
 * 09:38:10.401 ERROR [a1b2c3d4e5f6] [http-nio-8080-exec-1] c.t.T.e.GlobalExceptionHandler : 系统异常
 * </pre>
 *
 * <h2>顺序必须最靠前</h2>
 * <p>{@link Ordered#HIGHEST_PRECEDENCE} —— 比 IP 风控、JWT 鉴权都早。
 * 否则被风控拦掉、被鉴权拒掉的请求不会带 traceId,而那恰恰是最需要排查的请求。</p>
 *
 * <h2>安全:这个头是客户端可控的</h2>
 * <p>接受上游传来的 {@code X-Trace-Id} 是为了跨服务串联(网关生成 → 各服务沿用),
 * 但它<b>不能无条件采信</b>:攻击者可以塞 {@code "xxx\n2026-01-01 ERROR 伪造的日志行"},
 * 在日志文件里<b>伪造日志行</b>(log injection),把审计线索搅乱。</p>
 * <p>所以只接受 {@code [A-Za-z0-9_-]{1,32}} 这个白名单字符集 —— 换行、空格、
 * 控制字符一律拒收,改用自己生成的 ID。</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /** MDC 里的键名 —— logback 的 {@code %X{traceId}} 读的就是它 */
    public static final String MDC_KEY = "traceId";

    /** 上游可以传这个头来串联链路(网关/其它服务) */
    public static final String TRACE_HEADER = "X-Trace-Id";

    /**
     * 只允许字母数字和 - _ ,长度 1-32。
     * <p>这个白名单是防日志注入的关键:换行符一旦进了日志,攻击者就能凭空造出
     * 一整行看起来像系统输出的假日志。</p>
     */
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");

    @Override
    protected void doFilterInternal(HttpServletRequest req, @NonNull HttpServletResponse resp,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String traceId = resolveTraceId(req);
        MDC.put(MDC_KEY, traceId);
        // 回写响应头:前端报错时能把这个 ID 一起报上来,直接定位到那一次请求
        resp.setHeader(TRACE_HEADER, traceId);
        try {
            chain.doFilter(req, resp);
        } finally {
            // ★ 必须清理 —— Tomcat 的线程是复用的,不清理的话下一个请求会继承
            //   上一个请求的 traceId,日志会张冠李戴,比没有 traceId 更糟
            MDC.remove(MDC_KEY);
        }
    }

    /** 上游给了合法 ID 就沿用(跨服务串联),否则自己生成一个 */
    private String resolveTraceId(HttpServletRequest req) {
        String fromUpstream = req.getHeader(TRACE_HEADER);
        if (fromUpstream != null && SAFE_TRACE_ID.matcher(fromUpstream).matches()) {
            return fromUpstream;
        }
        // 16 位十六进制:比 UUID 短(日志里不喧宾夺主),碰撞概率对单机日志足够
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
