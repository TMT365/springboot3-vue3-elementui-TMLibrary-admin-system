package com.tmt.TMLibrary.dto.response;

import com.tmt.TMLibrary.entity.Order;
import com.tmt.TMLibrary.entity.OrderWithItems;
import lombok.Data;
import com.tmt.TMLibrary.common.Order.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 完整的订单响应 — 订单信息 + 明细列表。
 * <p>
 * Controller 端用 PurchaseResponse.from(OrderWithItems) 把 Service 返回的实体
 * 转成"给客户端的契约表示" — 隐藏内部字段(自增 id / orderId),
 * 暴露订单号、状态、金额、明细,以及两个时间(下单 / 支付)。
 */
@Data
public class PurchaseResponse {
    private String orderNumber;
    private OrderStatus status;
    private BigDecimal totalAmount;
    /** 明细列表 — 用 PurchaseItemResponse 而不是 Request,语义更清晰 */
    private List<PurchaseItemResponse> items;

    /**
     * 下单时间 —— 即 orders.created_time。
     * <p>序列化成 "yyyy-MM-ddTHH:mm:ss"(秒级截断,见 {@link #truncate})。</p>
     */
    private LocalDateTime createdTime;

    /**
     * 支付时间 —— orders.paid_time。
     * <p><b>未支付时为 null</b>(待支付 / 已取消 / 超时都没有支付时刻),
     * 前端要按 null 处理,不能当成 0 或空串。</p>
     */
    private LocalDateTime paidTime;

    /**
     * 支付方式 —— orders.payment_method,取值 WECHAT / ALIPAY / QQ。
     * <p><b>未支付时为 null</b>;另外,加这一列之前就已支付的老订单也是 null ——
     * 当时压根没记录,不能编一个值出来。前端把 null 渲染成「-」。</p>
     */
    private String paymentMethod;

    /**
     * 从 OrderWithItems 实体构造响应 — 排除 id / orderId 等内部字段。
     * 整体映射在 Controller 调用,Service 不知道这层。
     */
    public static PurchaseResponse from (OrderWithItems owi) {
        if (owi == null || owi.getOrder() == null) {
            return null;
        }
        PurchaseResponse r = new PurchaseResponse();
        r.setOrderNumber(String.valueOf(owi.getOrderNumber()));
        r.setStatus(OrderStatus.getOrderStatusByCode(owi.getOrder().getOrderStatus()));
        r.setTotalAmount(owi.getOrder().getTotalAmount());

        Order o = owi.getOrder();
        r.setCreatedTime(truncate(o.getCreatedTime()));
        r.setPaidTime(truncate(o.getPaidTime()));
        r.setPaymentMethod(o.getPaymentMethod());

        if (owi.getItems() != null) {
            r.setItems(owi.getItems().stream()
                .map(PurchaseItemResponse::from)
                .collect(Collectors.toList()));
        }
        return r;
    }

    /**
     * 截到秒 —— MySQL DATETIME 没有纳秒,但 Jackson 会把 LocalDateTime 序列化成
     * 带小数秒的形式,而 Safari 的 {@code new Date()} 解析不了超过 3 位的小数秒。
     * 截断后前端可以安全地直接 new Date(...)。
     */
    private static LocalDateTime truncate(LocalDateTime t) {
        return t == null ? null : t.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    }
}