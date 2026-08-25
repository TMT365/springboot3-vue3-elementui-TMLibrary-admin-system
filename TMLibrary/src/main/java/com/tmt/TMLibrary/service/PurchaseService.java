package com.tmt.TMLibrary.service;

import com.tmt.TMLibrary.entity.Order;
import com.tmt.TMLibrary.entity.OrderWithItems;
import com.tmt.TMLibrary.entity.OrderItem;
import java.util.List;
import com.tmt.TMLibrary.dto.PurchaseRequest;
/**
 * PurchaseService 是一个接口，定义了购买服务的相关操作。它可能包含与订单处理、支付、库存管理等相关的方法。具体实现类将根据业务需求实现这些方法。
 * 提供已下接口：
 * <ul>
 * <li>创建订单：处理用户的购买请求，生成订单并返回订单信息。</li>
 * <li>查询订单状态：根据订单ID查询订单的当前状态。</li>
 * <li>根据用户ID查询订单：获取指定用户的所有订单信息。</li>
 * <li>更新订单状态：根据订单ID更新订单的状态，例如支付成功、发货、取消等。</li>
 * <li>支付订单：处理订单的支付逻辑，包括调用支付网关、更新订单状态等。</li>
 * <li>取消订单：根据订单ID取消订单，并进行相应的库存和支付处理。</li>
 * <li>获取订单详情：根据订单ID获取订单的详细信息，包括订单项、总金额、支付方式等。</li>
 * </ul>
 * PurchaseService
 */
public interface PurchaseService {
    /**
     * 创建订单
     * @param userId 用户ID
     * @param shippingAddress 收货地址, 暂时为空
     * @param billingAddress 开票地址，暂时为空
     * @return 订单ID
     */
    public abstract int createOrder(PurchaseRequest purchaseRequest, Integer currentUserId);

    public abstract Order getOrderById(Integer orderId);

    /**
     * 根据订单ID获取订单及其订单项
     * @param orderId
     * @return
     */
    public abstract OrderWithItems getOrderWithItemsByOrderId(Integer orderId);

    public abstract OrderItem getOrderItemById(Integer orderItemId);
 
    public abstract List<OrderWithItems> listOrdersByUserId(Integer currentUserId);

    public abstract int cancelOrder(Integer orderId, Integer currentUserId);

    public abstract int payOrder(Integer orderId, Integer currentUserId, String paymentMethod);

}
