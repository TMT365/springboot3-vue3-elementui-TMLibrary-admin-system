package com.tmt.TMLibrary.dto;

import com.tmt.TMLibrary.entity.OrderItem;
import lombok.Data;
import java.math.BigDecimal;

/**
 * @brief 单个订单项的响应对象 — 服务器返回给客户端看的"一行明细"。
 *        <p>
 *        字段刻意只暴露客户端需要的:bookId(跳转图书详情)、quantity、
 *        price_snapshot(下单时的快照价,不受 book.price 后改影响)、subtotal。
 *        不暴露 id / orderId / createdTime 等内部字段。
 */
@Data
public class PurchaseItemResponse {
    private Integer bookId;
    private Integer quantity;
    private BigDecimal price;       // 下单时快照
    private BigDecimal subtotal;    // price × quantity
    // 这里以后可以加上 bookTitle / bookCoverUrl 等冗余字段,方便客户端显示,但不影响数据库设计。

    /**
     * 从 OrderItem 实体构造响应 — 排除内部字段,自动算 subtotal。
     * price 或 quantity 为 null 时 subtotal 兜底为 null(避免 NPE)。
     */
    public static PurchaseItemResponse from(OrderItem item) {
        PurchaseItemResponse r = new PurchaseItemResponse();
        r.setBookId(item.getBookId());
        r.setQuantity(item.getQuantity());
        r.setPrice(item.getPrice());
        if (item.getPrice() != null && item.getQuantity() != null) {
            r.setSubtotal(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        return r;
    }
}

