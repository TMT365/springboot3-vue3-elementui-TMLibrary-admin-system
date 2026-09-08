package com.tmt.TMLibrary.service;

import com.tmt.TMLibrary.entity.Order;
import com.tmt.TMLibrary.entity.OrderWithItems;
import com.tmt.TMLibrary.entity.OrderItem;
import java.util.List;
import com.tmt.TMLibrary.dto.request.PurchaseRequest;

/**
 * <h1>PurchaseService — 订单生命周期门面</h1>
 *
 * <p>暴露 5 类核心操作：</p>
 *
 * <ul>
 *   <li><strong>创建</strong>：{@code createOrder} — 下单 + 库存预占</li>
 *   <li><strong>查询</strong>：{@code getOrderByOrderNumber} / {@code getOrderWithItemsByOrderNumber} / {@code listOrdersByUserId}</li>
 *   <li><strong>取消</strong>：{@code cancelOrder} — 用户主动取消 + 释放库存</li>
 *   <li><strong>支付</strong>：{@code payOrder} — 状态翻转 + 确认库存扣减</li>
 *   <li><strong>系统关单</strong>：{@code cancelExpiredOrder} — 定时任务调用，<strong>不校验用户状态</strong></li>
 * </ul>
 *
 * <h2>状态机</h2>
 *
 * <pre>
 *             createOrder
 *                ↓
 *             PENDING ──payOrder──→ PAID
 *                ↓
 *   cancelOrder / cancelExpiredOrder
 *                ↓
 *             CANCELLED
 * </pre>
 *
 * @see com.tmt.TMLibrary.service.impl.PurchaseServiceImpl
 */
public interface PurchaseService {

    /**
     * 创建订单。
     *
     * <p>流程：
     * <ol>
     *   <li>校验用户状态（Redis 缓存）</li>
     *   <li>对每个 OrderItem：DB 读价格 → Lua 预占库存（异常时反向释放）</li>
     *   <li>插 {@code orders} + {@code order_items}</li>
     *   <li>写 Redis 双 key：Hash 数据 + ZSet 历史/超时索引</li>
     * </ol>
     *
     * @param purchaseRequest 下单请求（含 items 列表）
     * @param currentUserId   当前登录用户 ID
     * @return 新订单的 ID（DB 主键）
     */
    int createOrder(PurchaseRequest purchaseRequest, Integer currentUserId);

    /**
     * 根据订单号查订单（不含 items）。
     *
     * @param orderNumber Snowflake 生成的订单号
     * @return Order 实体，不存在返回 {@code null}
     */
    Order getOrderByOrderNumber(Long orderNumber);

    /**
     * 根据订单号查订单（含 items）。
     *
     * @param orderNumber Snowflake 生成的订单号
     * @return OrderWithItems，不存在返回 {@code null}
     */
    OrderWithItems getOrderWithItemsByOrderNumber(Long orderNumber);

    /**
     * 查某用户的所有订单（含 items）。
     *
     * @param currentUserId 用户 ID
     * @return 订单列表，按创建时间排序
     */
    List<OrderWithItems> listOrdersByUserId(Integer currentUserId);

    /**
     * 用户主动取消订单。
     *
     * <p>校验用户状态 + 订单归属 + 订单状态（PENDING 才允许取消）。
     * 释放库存预占。
     *
     * @param orderNumber   订单号
     * @param currentUserId 当前用户 ID（用于校验归属）
     * @return {@code 1} = 成功
     */
    int cancelOrder(Long orderNumber, Integer currentUserId);

    /**
     * 支付订单（状态翻转）。
     *
     * <p><b>当前未接支付网关</b>，仅翻状态为 PAID + 确认库存扣减。
     * 真实接入支付时，需要：
     * <ol>
     *   <li>生成支付订单（调支付宝/微信 API）</li>
     *   <li>等回调</li>
     *   <li>回调验签成功后调本方法</li>
     * </ol>
     *
     * @param orderNumber   订单号
     * @param currentUserId 当前用户 ID
     * @param paymentMethod 支付方式（ALIPAY / WECHAT / ...），预留字段
     * @return {@code 1} = 成功
     */
    int payOrder(Long orderNumber, Integer currentUserId, String paymentMethod);

    /**
     * 系统主动关单 — 用于定时任务扫描超时订单。
     *
     * <p><b>不校验用户状态</b>（订单已超时，用户禁用与否不影响关单）。
     * 幂等：状态非 PENDING 时仅清理 expire idx 不抛异常。
     *
     * @param orderNumber 订单号
     */
    void cancelExpiredOrder(Long orderNumber);
}
