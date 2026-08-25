package com.tmt.TMLibrary.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class OrderItem {
    private Integer id;
    private Integer orderId;
    private Integer bookId;
    private Integer quantity;
    // BigDecimal 在创建对象时要使用字符串创建
    private BigDecimal price;
    private LocalDateTime createdTime;
}
