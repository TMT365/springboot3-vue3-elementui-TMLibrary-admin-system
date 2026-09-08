package com.tmt.TMLibrary.common.User;

import com.tmt.TMLibrary.common.Result.ResultCode;
import com.tmt.TMLibrary.exception.BusinessException;
import lombok.Getter;

//这个类定义了用户状态的枚举类型，用于表示用户账户的不同状态。
@Getter
public enum UserStatus {
    // ACTIVE表示账户正常
    ACTIVE(0, "Active"),
    // INACTIVE表示账户未激活
    INACTIVE(1, "Inactive"),
    // SUSPENDED表示账户被暂停，封号
    SUSPENDED(2, "Suspended");

    private final Integer code;
    private final  String description;

    UserStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public UserStatus getUserStatusByCode(Integer code) {
        for (UserStatus status : UserStatus.values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new BusinessException(ResultCode.NOT_FOUND, "Unknown role code: " + code);
    }
}
