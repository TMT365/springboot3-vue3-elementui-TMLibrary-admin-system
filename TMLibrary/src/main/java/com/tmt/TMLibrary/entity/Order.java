package com.tmt.TMLibrary.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.tmt.TMLibrary.common.Order.OrderStatus;

import lombok.Data;

@Data
public class Order {
    // 主键
    private Integer id;
    private Long orderNumber;
    private Integer userId;
    // BigDecimal 在创建对象时要使用字符串创建
    private BigDecimal totalAmount;
    private Integer orderStatus;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    // 过期时间
    private LocalDateTime expireTime;

    /** 支付时间 —— 只有 PENDING → PAID 那次流转会写;未支付为 null */
    private LocalDateTime paidTime;

    /**
     * 支付方式 —— 和 paidTime 在同一次状态流转里写入,未支付为 null。
     * <p>存的是 {@link com.tmt.TMLibrary.common.Order.PaymentMethod} 的 code
     * (WECHAT / ALIPAY / QQ)。迁移前已支付的老订单这里是 null ——
     * 当时压根没记录,不能瞎猜填一个,前端按「-」展示。</p>
     */
    private String paymentMethod;
}
