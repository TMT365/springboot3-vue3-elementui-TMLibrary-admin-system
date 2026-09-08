package com.tmt.TMLibrary.common.Result;

import lombok.Data;

//后端返回给前端的标准接口，所有请求的封装
@Data
public class Result<T> {
    
    private Integer code; // 状态码
    private String msg;   // 消息
    private T data;  // 数据

    public Result() {
    }

    public Result(Integer code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    // 简单成功返回函数
    public static <T> Result<T> success() {
        return success(null);
    }

    // 带数据返回的成功函数
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.code = ResultCode.SUCCESS.getCode();
        result.msg = ResultCode.SUCCESS.getMessage();
        result.data = data;
        return result;
    }

    // 返回标准失败编码函数（推荐）
    public static <T> Result<T> fail(ResultCode rc, String msg) {
        return fail(rc.getCode(), msg);
    }
    // 返回自定义失败编码函数
    public static <T> Result<T> fail(int code, String msg) {
        Result<T> result = new Result<>();
        result.code = code;
        result.msg = msg;
        result.data = null;
        return result;
    }
}
