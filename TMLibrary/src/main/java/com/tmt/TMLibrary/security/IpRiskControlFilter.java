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
import java.util.Set;

/**
 * IP 风控过滤器 —— 每个 {@code /*} 请求都过一遍。
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

    /**
     * actuator 前缀 —— 健康检查/指标端点不参与风控。
     *
     * <p>过滤器注册 pattern 从 {@code /api/*} 放宽到 {@code /*} 后,容器的
     * healthcheck 也会进这条链;而生产配置 {@code trust-private-ips=false}
     * (见 application-prod.yml),回环地址不再被 {@link #isTrustedAddress} 豁免。
     * 于是只要 127.0.0.1 因真实流量触发封禁,健康检查会跟着吃 429 →
     * 容器被判 unhealthy → 重启 → 而封禁还在 Redis 里 → 抖动满整个封禁时长。
     * 改动前 actuator 根本不在链上,这里是为了保持原有行为。</p>
     */
    private static final String ACTUATOR_PREFIX = "/actuator";

    private final IpBanService ipBanService;
    private final AuthErrorWriter errorWriter;
    /**
     * 是否把回环/私网地址视为可信(默认 true)。
     * <p>本地演示风控效果时设 {@code app.security.ip-ban.trust-private-ips=false},
     * 本机请求也会参与计数,就能触发封禁看到前端弹窗 —— 演示完记得改回来。</p>
     */
    private final boolean trustPrivateIps;

    /**
     * 可信反向代理的地址名单(精确匹配)。
     *
     * <p><b>默认为空 = 不信任任何代理头</b>,直接用 remoteAddr。这是安全的默认值:
     * 直连部署、容器直连、内网调用都该走这条路径。</p>
     *
     * <p>只有当应用挂在 Nginx / 网关后面,且网关会<b>覆盖</b>(而不是透传)
     * {@code X-Forwarded-For} 时,才把网关的地址填进来,例如
     * {@code app.security.ip-ban.trusted-proxies=127.0.0.1,10.0.0.5}。</p>
     */
    private final Set<String> trustedProxies;

    public IpRiskControlFilter(IpBanService ipBanService, AuthErrorWriter errorWriter,
                               boolean trustPrivateIps, Set<String> trustedProxies) {
        this.ipBanService = ipBanService;
        this.errorWriter = errorWriter;
        this.trustPrivateIps = trustPrivateIps;
        this.trustedProxies = trustedProxies == null ? Set.of() : trustedProxies;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, @NonNull HttpServletResponse resp,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        // OPTIONS 预检不算请求量(CORS 由 WebMvcConfig 处理)
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            chain.doFilter(req, resp);
            return;
        }

        // actuator 不计数(理由见 ACTUATOR_PREFIX 的注释)
        if (req.getRequestURI().startsWith(ACTUATOR_PREFIX)) {
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
     * 取客户端真实 IP —— <b>只在直连方是可信代理时才读代理头</b>。
     *
     * <h2>为什么不能无条件读 X-Forwarded-For</h2>
     * <p>这个头是客户端可以随便写的。早期版本直接取它的第一段,等于:
     * 攻击者只要每次请求换一个 {@code X-Forwarded-For: 1.2.3.4} 的值,
     * 风控就永远按不同的 IP 计数 —— <b>封禁完全失效</b>。
     * 更糟的是他还能伪造别人的 IP 把别人封掉。</p>
     *
     * <h2>正确做法</h2>
     * <ol>
     *   <li>直连方({@code remoteAddr})不在可信代理名单里 → 头一律不认,就用 remoteAddr。
     *       默认名单为空 = 永远不认头,这是最安全的默认值(直连部署)。</li>
     *   <li>直连方是可信代理 → 从 XFF <b>右侧往左</b>扫,跳过可信代理,
     *       取第一个不可信的地址。这样客户端自己塞在左边的假地址会被忽略,
     *       真正由我们代理追加的那一段才会被采信。</li>
     * </ol>
     *
     * <p>例:客户端发 {@code X-Forwarded-For: 9.9.9.9},我们的 Nginx 追加真实来源后
     * 变成 {@code 9.9.9.9, 203.0.113.7}。从右往左:203.0.113.7 不可信 → 采用它,
     * 而 9.9.9.9 被跳过。这正是 nginx realip / Express {@code trust proxy} 的算法。</p>
     */
    private String resolveClientIp(HttpServletRequest req) {
        String remoteAddr = req.getRemoteAddr();
        if (!isTrustedProxy(remoteAddr)) {
            // 直连部署 / 不可信来源:头是客户端可控的,不认
            return remoteAddr;
        }
        for (String header : IP_HEADERS) {
            String value = req.getHeader(header);
            if (value == null || value.isBlank() || "unknown".equalsIgnoreCase(value)) {
                continue;
            }
            // XFF 形如 "client, proxy1, proxy2" —— 从右往左找第一个不可信的
            String[] hops = value.split(",");
            for (int i = hops.length - 1; i >= 0; i--) {
                String hop = hops[i].trim();
                if (hop.isEmpty()) {
                    continue;
                }
                if (!isTrustedProxy(hop)) {
                    return hop;
                }
            }
        }
        return remoteAddr;
    }

    /** 直连方是否在可信代理名单里(见 {@code app.security.ip-ban.trusted-proxies}) */
    private boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isBlank() || trustedProxies.isEmpty()) {
            return false;
        }
        return trustedProxies.contains(ip);
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
