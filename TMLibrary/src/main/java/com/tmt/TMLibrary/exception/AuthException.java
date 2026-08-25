package com.tmt.TMLibrary.exception;

import com.tmt.TMLibrary.common.Result.ResultCode;

import lombok.Getter;

@Getter
public class AuthException extends BusinessException {
    public AuthException(ResultCode resultCode) {
        super(resultCode);
    }

    public AuthException(ResultCode resultCode, String message) {
        super(resultCode, message);
    }

    public AuthException(int code, String message) {
        super(code,message);
    }

}