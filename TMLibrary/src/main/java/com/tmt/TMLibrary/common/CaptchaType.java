package com.tmt.TMLibrary.common;

/**
 * Captcha 用途 —— 决定 Redis key 走 {@code login:{uuid}} 还是 {@code register:{uuid}} 路径。
 * <br>两个路径的 captcha 元数据互不干扰:登录 captcha 不能拿去注册,反之亦然。
 */
public enum CaptchaType {
    LOGIN,
    REGISTER,
}
