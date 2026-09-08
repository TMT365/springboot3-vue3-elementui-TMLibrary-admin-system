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