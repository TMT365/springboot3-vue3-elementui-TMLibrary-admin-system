package com.tmt.TMLibrary.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 验证码响应 —— 2026-09 改为 JSON 对象(原是 image/png 二进制流)。
 * <p>前端拿 {@code image} data URI 直接当 {@code <img src>} 用,
 * 拿 {@code expiresAt} 字符串时间戳做倒计时显示。</p>
 *
 * <h3>为什么 expiresAt 用 String 而不是 long?</h3>
 * JS 的 {@code Number} 是 64-bit 浮点,安全整数范围 ±2^53(≈ 9×10^15);
 * 当前毫秒时间戳 ~1.7×10^12 还在范围内,但:
 * <ul>
 *   <li>Java 后端 {@code long} 范围是 ±9.2×10^18,某些序列化路径(微秒/纳秒精度)会越界</li>
 *   <li>前端某些 JSON 解析器(老浏览器)会丢精度</li>
 *   <li>String 在前后端都是无损的,前端 {@code Number(str)} 解析毫秒时间戳永远不会出问题</li>
 * </ul>
 * 这是「string at the boundary」惯例 —— 内部用 long 算时间,跨网络用 string 传输。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaResponse {
    /** base64 data URI,可直接作为 &lt;img src&gt; 使用 */
    private String image;
    /** 过期时间戳(毫秒,字符串形式),前端 {@code Number(str)} 解析后做倒计时 */
    private String expiresAt;
}

