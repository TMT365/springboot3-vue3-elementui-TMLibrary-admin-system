package com.tmt.TMLibrary.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 「某天 + 订单数 + 销售额」聚合行 —— 销量趋势用。
 * <p>只统计 {@code order_status = 1}(PAID):待支付/已取消/超时都不算成交。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DailySalesItem {
    /** 日期(无时区) */
    private LocalDate date;
    /** 当天成交订单数 */
    private long orders;
    /** 当天成交金额(SUM(total_amount),下单时快照价) */
    private BigDecimal amount;
}
