package com.tmt.TMLibrary.common.utils;

import com.tmt.TMLibrary.common.Result.ResultCode;
import com.tmt.TMLibrary.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.client.RestTemplate;
import com.tmt.TMLibrary.vo.IpApiVo;
import org.springframework.web.util.InvalidUrlException;

/**
 * 获取用户登录IP的工具，用户的请求地址IP被封装在 {@link jakarta.servlet.http.HttpServletRequest} 里面
 * 生产调用 ip2region，现在使用第三方API接口 {https://ipinfo.io/}和{https://ip-api.com/}
 */

public class IpUtil {

    private static final String ACQUIRE_URL = "http://ip-api.com/json/{query}?fields=57631";

    private static final RestTemplate restTemplate;

    static {
        restTemplate = new RestTemplate();
    }

    /**
     * 仅解析客户端真实 IP —— 纯本地计算,<b>不发起任何网络请求</b>。
     *
     * <p>按代理链逐级回退:X-Forwarded-For → Proxy-Client-IP → WL-Proxy-Client-IP
     * → X-Real-IP → {@code request.getRemoteAddr()}。</p>
     *
     * <p>用于登录时记录 {@code last_login_ip}。与 {@link #getClientIp(HttpServletRequest)}
     * 的区别:后者会调用外部 API 做地理位置解析,属于阻塞式远程调用,
     * <b>不应放在登录热路径上</b>。</p>
     *
     * @return 客户端 IP;无法解析时返回空串(不返回 null,便于直接落库)
     */
    public static String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String ip = firstNonBlank(
            request.getHeader("X-Forwarded-For"),
            request.getHeader("Proxy-Client-IP"),
            request.getHeader("WL-Proxy-Client-IP"),
            request.getHeader("X-Real-IP"),
            request.getRemoteAddr()
        );
        if (ip == null) {
            return "";
        }
        // 经过多级代理时取第一个(最靠近客户端的)地址
        int comma = ip.indexOf(',');
        if (comma > 0) {
            ip = ip.substring(0, comma);
        }
        ip = ip.trim();
        // IPv6 回环地址归一化,便于阅读与筛选
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return "127.0.0.1";
        }
        return ip;
    }

    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank() && !"unknown".equalsIgnoreCase(c.trim())) {
                return c;
            }
        }
        return null;
    }

    public static IpApiVo getClientIp(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("X-Real-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }
        // 如果通过多个代理，获取第一个IP地址
        if (ipAddress != null && ipAddress.contains(",")) {
            ipAddress = ipAddress.split(",")[0].trim();
        }

        // restTemplate.getForObject()有支持对url的模板字符串，导致事先拼接好的url无法正常使用，2种解决方法：
        // 1. 使用uri对象
        // 2. 使用url模板字符串

        String url = "http://ip-api.com/json/{ip}?fields=57631";

        return restTemplate.getForObject(url, IpApiVo.class, ipAddress);
    }
}