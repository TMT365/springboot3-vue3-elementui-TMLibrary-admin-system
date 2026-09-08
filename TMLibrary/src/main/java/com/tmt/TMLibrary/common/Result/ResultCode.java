package com.tmt.TMLibrary.common.Result;

import lombok.Getter;

@Getter
public enum ResultCode {
    SUCCESS(200, "成功"),
    CREATED(201, "创建成功"),
    NO_CONTENT(204, "无返回内容"),

    NOT_MODIFIED(304, "资源未修改"),

    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未认证或认证失败"),
    FORBIDDEN(403, "权限不足，禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405,"请求方法不允许"),
    REQUEST_TIMEOUT(408, "请求超时"),
    CONFLICT(409, "请求条件冲突"),
    PAYLOAD_TOO_LARGE(413, "请求体过大"),
    UNSUPPORTED_MEDIA_TYPE(415, "媒体类型不支持"),
    UNPROCESSABLE_ENTITY(422, "业务校验失败"),

    INTERNAL_ERROR(500, "服务器内部错误"),
    BAD_GATEWAY(502, "网关错误"),
    SERVICE_UNAVAILABLE(503, "服务不可用"),
    GATEWAY_TIMEOUT(504, "网关超时");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}