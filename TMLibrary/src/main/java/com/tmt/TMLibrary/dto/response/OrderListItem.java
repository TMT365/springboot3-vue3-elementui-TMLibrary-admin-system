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

    public static OrderListItem from(Order o) {
        if (o == null) {
            return null;
        }
        OrderListItem item = new OrderListItem();
        item.setOrderNumber(String.valueOf(o.getOrderNumber()));
        item.setStatus(OrderStatus.getOrderStatusByCode(o.getOrderStatus()));
        item.setTotalAmount(o.getTotalAmount());
        item.setCreatedTime(o.getCreatedTime());
        return item;
    }
}
