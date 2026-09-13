package com.tmt.TMLibrary.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 登出响应 — 包含明确"redirect"信号,前端拿到后应跳转回登录/欢迎页。
 *
 * <p>为什么不用简单的 {@code Result.success(null)}?
 * 后端无法真的"重定向"客户端;只能通过响应体告诉前端"该跳走了"。
 * 前端在拦截器里看到 {@code loggedOut=true} 就清 localStorage + router.push('/login')。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogoutResponse {
    /** 固定 true — 前端以此判断是否需要跳转 */
    private boolean loggedOut;
    /** 提示文案,直接展示给用户 */
    private String message;
    /** 建议前端跳转的路径 */
    private String redirectUrl;
    /** 服务端登出时间(ISO-8601),前端可用于显示"已于 X 点登出" */
    private Instant logoutAt;
}