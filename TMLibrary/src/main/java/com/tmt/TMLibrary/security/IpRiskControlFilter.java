package com.tmt.TMLibrary.security;

import com.tmt.TMLibrary.common.Result.ResultCode;
import com.tmt.TMLibrary.dto.response.IpBanInfo;
import com.tmt.TMLibrary.service.IpBanService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * IP 风控过滤器 —— 每个 {@code /api/*} 请求都过一遍。
 *
 * <h2>顺序</h2>
 * 注册在 {@link com.tmt.TMLibrary.security.jwt.JwtAuthFilter} 之前(order 5 &lt; 10):
 * 已经封禁的 IP 连 token 都不用解析,直接打回,省掉后续所有开销。
 *
 * <h2>两件事</h2>
 * <ol>
 *   <li><b>查</b>:Redis 里有没有该 IP 的封禁标记 → 有就 429 + 封禁详情</li>
 *   <li><b>记</b>:没有就计数(固定窗口),超阈值 → 封禁并 429</li>
 * </ol>
 *
 * <h2>为什么不封这些 IP</h2>
 * 回环 / 私网地址(127.0.0.1、::1、10.x、192.168.x、172.16-31.x)一律跳过 ——
 * 本地开发和内网调用不该触发风控,否则开发时自己就把自己封了。
 */
@Slf4j
public class IpRiskControlFilter extends OncePerRequestFilter {

    /** 网关转发时真实 IP 所在的请求头,按优先级取第一个非空的 */
    private static final String[] IP_HEADERS = {
            "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"
    };

    private final IpBanService ipBanService;
    private final AuthErrorWriter errorWriter;
    /**
     * 是否把回环/私网地址视为可信(默认 true)。
     * <p>本地演示风控效果时设 {@code app.security.ip-ban.trust-private-ips=false},
     * 本机请求也会参与计数,就能触发封禁看到前端弹窗 —— 演示完记得改回来。</p>
     */
    private final boolean trustPrivateIps;

    public IpRiskControlFilter(IpBanService ipBanService, AuthErrorWriter errorWriter,
                               boolean trustPrivateIps) {
        this.ipBanService = ipBanService;
        this.errorWriter = errorWriter;
        this.trustPrivateIps = trustPrivateIps;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, @NonNull HttpServletResponse resp,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        // OPTIONS 预检不算请求量(CORS 由 WebMvcConfig 处理)
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            chain.doFilter(req, resp);
            return;
        }

        String ip = resolveClientIp(req);

        // 本地 / 内网地址直接放行
        if (isTrustedAddress(ip)) {
            chain.doFilter(req, resp);
            return;
        }

        // 1. 已封禁?
        IpBanInfo active = ipBanService.getActiveBan(ip);
        if (active != null) {
            rejectBanned(resp, active, "已封禁 IP 再次请求");
            return;
        }

        // 2. 计数 + 检测(超阈值会顺手封禁)
        IpBanInfo triggered = ipBanService.recordAndDetect(ip);
        if (triggered != null) {
            rejectBanned(resp, triggered, "本次请求触发封禁");
            return;
        }

        chain.doFilter(req, resp);
    }

    /** 写 429 + 封禁详情 —— 前端识别 code=429 后弹「访问已被限制」弹窗 */
    private void rejectBanned(HttpServletResponse resp, IpBanInfo info, String logHint)
            throws IOException {
        log.warn("风控拦截: ip={}, 剩余 {} 秒, 原因={} ({})",
                info.getIp(), info.getRemainingSeconds(), info.getReason(), logHint);
        errorWriter.writeError(resp, ResultCode.TOO_MANY_REQUESTS.getCode(),
                "访问已被限制,请在解封后重试", info);
    }

    /**
     * 取客户端真实 IP。
     * <p>优先读代理头(网关转发场景),都没有才用 remoteAddr。
     * <b>注意</b>:这些头可被伪造,只有在可信反向代理后面才安全 ——
     * 本项目的部署形态(单实例直连)下 remoteAddr 就是真实来源;
     * 将来上网关时应在网关层覆盖 X-Forwarded-For,而不是透传客户端传的值。</p>
     */
    private String resolveClientIp(HttpServletRequest req) {
        for (String header : IP_HEADERS) {
            String value = req.getHeader(header);
            if (value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value)) {
                // X-Forwarded-For 可能是 "client, proxy1, proxy2" —— 第一个是原始客户端
                int comma = value.indexOf(',');
                return (comma > 0 ? value.substring(0, comma) : value).trim();
            }
        }
        return req.getRemoteAddr();
    }

    /** 回环 / 私网地址 —— 不参与风控(trustPrivateIps=false 时关闭该保护,用于本地演示) */
    private boolean isTrustedAddress(String ip) {
        if (!trustPrivateIps) {
            return false;
        }
        if (ip == null || ip.isBlank()) {
            return true; // 拿不到 IP 就不要误伤
        }
        if ("127.0.0.1".equals(ip) || "::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
            return true;
        }
        // IPv4 私网三段:10.0.0.0/8、172.16.0.0/12、192.168.0.0/16
        if (ip.startsWith("10.") || ip.startsWith("192.168.")) {
            return true;
        }
        if (ip.startsWith("172.")) {
            String[] parts = ip.split("\\.");
            if (parts.length >= 2) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    return second >= 16 && second <= 31;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        // IPv6 回环 / 链路本地
        return ip.startsWith("fe80:") || ip.startsWith("fc") || ip.startsWith("fd");
    }
}
