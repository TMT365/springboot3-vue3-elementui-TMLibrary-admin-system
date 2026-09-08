package com.tmt.TMLibrary.exception;

import com.tmt.TMLibrary.common.Result.ResultCode;

import lombok.Getter;

/**
 * 业务异常类，用于表示业务逻辑中的异常情况。
 * 异常会自动冒泡到 GlobalExceptionHandler，被它接住转成 Result.fail(404, "图书不存在,id=5")。
 * 
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause); // super把原始异常交给父类Throwable保存
        this.code = code;
    }

    public BusinessException(ResultCode resultCode, String message, Throwable cause) {
        super(message, cause); // super把原始异常交给父类Throwable保存
        this.code = resultCode.getCode();
    }


}
