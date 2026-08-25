package com.tmt.TMLibrary.security;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import com.tmt.TMLibrary.common.Result.Result;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthErrorWriter {
    // Spring 框架默认提供objectMapper单个Bean,直接使用构造器注入就行
    private final ObjectMapper objectMapper;

    public void writeAuthError(HttpServletResponse response, String errorMessage) {
        writeError(response, 401, errorMessage);
    }
    /**
     * 将异常信息写入响应中,在spring里面，@RestController 会将异常信息转换为JSON格式返回给前端。
     * 其实就是调用 ObjectMapper 的 writeValueAsString 方法将异常信息转换为 JSON 格式,然后写入响应中。
     * @param response 响应对象
     * @param httpStatus HTTP状态码
     * @param errorMessage 错误信息
     */
    public void writeError(HttpServletResponse response, int httpStatus, String errorMessage) {
        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        try {
            String json = objectMapper.writeValueAsString(new Result<Void>(httpStatus, errorMessage, null));
            response.getWriter().write(json);
            response.getWriter().flush();
        } catch (Exception e) {
            log.error("写错误响应失败: status={}, msg={}", httpStatus, errorMessage, e);
        }
    }
}
