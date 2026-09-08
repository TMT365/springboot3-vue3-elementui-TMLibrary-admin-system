package com.tmt.TMLibrary.common.Order;
import com.tmt.TMLibrary.exception.BusinessException;
import com.tmt.TMLibrary.common.Result.ResultCode;
import lombok.Getter;

@Getter
public enum OrderStatus {
    // 未支付
    PENDING(0,"待支付"),
    // 已支付
    PAID(1,"已支付"),
    // 取消订单
    CANCELLED(2,"已取消"),
    // 订单超时
    TIMEOUT(3,"订单超时取消");

    private final Integer code;
    private final String description;

    OrderStatus(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    public static  OrderStatus getOrderStatusByCode(Integer code) {
        for (OrderStatus status : OrderStatus.values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new BusinessException(ResultCode.NOT_FOUND, "Unknown role code: " + code);
    }

}
