package com.tmt.TMLibrary.dto.response;

import com.tmt.TMLibrary.common.Order.OrderStatus;
import com.tmt.TMLibrary.entity.Order;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理端订单列表的一行 —— GET /purchases 的分页元素。
 *
 * <p>比 {@link PurchaseResponse} 轻:不带 items(列表页不展开明细),
 * 但补上 createdTime(列表要按时间展示;详情接口反而不暴露时间)。</p>
 */
@Data
public class OrderListItem {

    /** 雪花 ID 用字符串输出 —— Long 超过 JS 的 Number.MAX_SAFE_INTEGER,必须走字符串 */
    private String orderNumber;

    /** 枚举,Jackson 序列化为名字字符串("PENDING" / "PAID" / "CANCELLED" / "TIMEOUT") */
    private OrderStatus status;

    private BigDecimal totalAmount;

    private LocalDateTime createdTime;

    /**
     * 支付方式 —— WECHAT / ALIPAY / QQ;未支付为 null。
     *
     * <p>管理端要靠它核对"这单走的哪个渠道"。加这一列之前列表里完全看不到支付信息,
     * 用户付完款管理员也无从确认。</p>
     */
    private String paymentMethod;

    /**
     * 支付时间 —— 未支付为 null。
     * <p>和 paymentMethod 一起给:单看"支付方式"没有时间参照,对账时对不上。</p>
     */
    private LocalDateTime paidTime;

    public static OrderListItem from(Order o) {
        if (o == null) {
            return null;
        }
        OrderListItem item = new OrderListItem();
        item.setOrderNumber(String.valueOf(o.getOrderNumber()));
        item.setStatus(OrderStatus.getOrderStatusByCode(o.getOrderStatus()));
        item.setTotalAmount(o.getTotalAmount());
        item.setCreatedTime(o.getCreatedTime());
        item.setPaymentMethod(o.getPaymentMethod());
        item.setPaidTime(o.getPaidTime());
        return item;
    }
}
