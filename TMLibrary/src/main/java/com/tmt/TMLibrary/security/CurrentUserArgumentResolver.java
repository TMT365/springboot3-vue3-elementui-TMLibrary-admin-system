package com.tmt.TMLibrary.security;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import jakarta.servlet.http.HttpServletRequest;

/**
 * @brief @CurrentUser UserView 参数解析器。
 *        <p>
 *        从 HttpServletRequest attribute "CURRENT_USER" 读,
 *        写入 Controller 方法参数。
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        // 必须同时满足:
        // 1. 参数有 @CurrentUser 注解
        // 2. 参数类型是 UserView(或其子类)
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && UserView.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        // 从 NativeWebRequest 拿 HttpServletRequest(为了拿到 attribute)
        HttpServletRequest req = webRequest.getNativeRequest(HttpServletRequest.class);
        if (req == null) {
            return null; // 非 HTTP 请求(罕见,fallback null)
        }
        return CurrentUserContext.get(req);
        // 返回 null 表示"未登录",由 Controller 决定怎么兜底
        // 例如:AuthController 不要求 @CurrentUser,自然没值;
        // UserController.update() 应该 null 时抛 401
    }
}