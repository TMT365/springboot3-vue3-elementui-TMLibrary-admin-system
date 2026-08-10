package com.tmt.TMLibrary.common.Result;

import lombok.Data;
// import lombok.NoArgsConstructor;

// 这是一个简单的Result类，用于封装API的响应结果，包含状态码、消息和数据。
// 统一返回 `{code, msg, data}` 结构,前端永远只看 `code === 200`
// 静态工厂方法 `success()` / `fail()`,不要让外部直接 `new Result<>`
@Data
// @NoArgsConstructor
public class Result<T> {
    
    private Integer code; // 状态码
    private String msg;   // 消息
    private T data;  // 数据
    // data 可以是任何类型，例如对象、列表、字符串等。使用泛型 T 来表示数据类型，使得 Result 类可以适用于不同的返回数据类型。
    // Object 类型也行

    public Result() {
    }

    public Result(Integer code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.code = ResultCode.SUCCESS.getCode();
        result.msg = ResultCode.SUCCESS.getMessage();
        result.data = data;
        return result;
    }

    public static <T> Result<T> fail(ResultCode rc, String msg) {
        return fail(rc.getCode(), msg);
    }

    public static <T> Result<T> fail(int code, String msg) {
        Result<T> result = new Result<>();
        result.code = code;
        result.msg = msg;
        result.data = null;
        return result;
    }
}
