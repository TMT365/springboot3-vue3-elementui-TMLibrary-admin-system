package com.tmt.TMLibrary.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 「书 + 销量」聚合行 —— 每本书销量 Top N 用。
 * <p>数据来源:order_items ⋈ orders(仅 PAID)⋈ books。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BookSalesItem {
    private int bookId;
    private String title;
    private String author;
    /** 累计售出件数(SUM(quantity)) */
    private long quantity;
    /** 累计销售额(SUM(quantity × 下单时单价快照)) */
    private BigDecimal amount;
}
