package com.tmt.TMLibrary.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.tmt.TMLibrary.common.Order.OrderStatus;

import lombok.Data;

@Data
public class Order {
    // 主键
    private Integer id;
    private String orderNumber;
    private Integer userId;
    // BigDecimal 在创建对象时要使用字符串创建
    private BigDecimal totalAmount;
    private Integer orderStatus;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
