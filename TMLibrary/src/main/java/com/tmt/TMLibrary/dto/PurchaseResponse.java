package com.tmt.TMLibrary.dto;

import com.tmt.TMLibrary.entity.OrderWithItems;
import lombok.Data;
import com.tmt.TMLibrary.common.Order.OrderStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 完整的订单响应 — 订单信息 + 明细列表。
 * <p>
 * Controller 端用 PurchaseResponse.from(OrderWithItems) 把 Service 返回的实体
 * 转成"给客户端的契约表示" — 隐藏内部字段,只暴露这 4 个属性。
 */
@Data
public class PurchaseResponse {
    private String orderNumber;
    private OrderStatus status;
    private BigDecimal totalAmount;
    /** 明细列表 — 用 PurchaseItemResponse 而不是 Request,语义更清晰 */
    private List<PurchaseItemResponse> items;

    /**
     * 从 OrderWithItems 实体构造响应 — 排除 id / orderId / createdTime 等内部字段。
     * 整体映射在 Controller 调用,Service 不知道这层。
     */
    public static PurchaseResponse from (OrderWithItems owi) {
        if (owi == null || owi.getOrder() == null) {
            return null;
        }
        PurchaseResponse r = new PurchaseResponse();
        r.setOrderNumber(owi.getOrder().getOrderNumber());
        r.setStatus(OrderStatus.getOrderStatusByCode(owi.getOrder().getOrderStatus()));
        r.setTotalAmount(owi.getOrder().getTotalAmount());
        if (owi.getItems() != null) {
            r.setItems(owi.getItems().stream()
                .map(PurchaseItemResponse::from)
                .collect(Collectors.toList()));
        }
        return r;
    }
}