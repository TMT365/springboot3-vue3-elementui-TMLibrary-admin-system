package com.tmt.TMLibrary.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * @brief 当前用户的 request attribute 读写器。
 *        <p>
 *        Filter 写、Resolver 读,共享同一个 attribute 名常量,避免拼写错。
 */
public class CurrentUserContext {

    /** request attribute 键名,Filter 和 Resolver 都引用这个 */
    public static final String ATTR_CURRENT_USER = "CURRENT_USER";

    /** Filter 在 JwtAuthFilter.parse() 成功后调用 */
    public static void set(HttpServletRequest req, UserView user) {
        req.setAttribute(ATTR_CURRENT_USER, user);
    }

    /** Resolver 在解析 @CurrentUser 参数时调用 */
    public static UserView get(HttpServletRequest req) {
        return (UserView) req.getAttribute(ATTR_CURRENT_USER);
    }

    /** 通常用不到 — Filter 异常路径会调 */
    public static void clear(HttpServletRequest req) {
        req.removeAttribute(ATTR_CURRENT_USER);
    }
}